package sqlancer.common.oracle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.sqlengine.dialect.DialectFactory;
import com.sqlengine.dialect.DialectManager;
import com.sqlengine.dialect.DialectType;
import com.sqlengine.engine.Executor;
import com.sqlengine.env.Row;
import com.sqlengine.env.Table;

import sqlancer.ComparatorHelper;
import sqlancer.SQLGlobalState;
import sqlancer.common.query.Query;

public class ReferenceEngineOracleBase<S extends SQLGlobalState<?, ?>> {
    
    protected static class ExecutionResult {
        private final Throwable throwable;
        private final List<String> firstColResultSet;

        public ExecutionResult(Throwable throwable, List<String> firstColResultSet) {
            this.throwable = throwable;
            this.firstColResultSet = firstColResultSet;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public List<String> getFirstColResultSet() {
            return firstColResultSet;
        }
    }
    
    private final S globalState;
    private final DialectManager dialectManager;
    private Executor executor;

    public ReferenceEngineOracleBase(S globalState, DialectType dialectType) {
        this.globalState = globalState;
        this.dialectManager = DialectFactory.createDialectManager(dialectType);
        try {
            this.executor = this.initializeExecutor();
        } catch (Exception e) {
        }
    }

    private Executor initializeExecutor() throws Exception {
        Executor executor = new Executor(dialectManager);
        executor.setIgnoreInsertErrors(true);

        StringBuilder statements = new StringBuilder();
        for (Query<?> queryStatement : globalState.getState().getStatements()) {
            String queryStatementStr = queryStatement.toString();
            if (
                queryStatementStr.startsWith("DROP DATABASE") ||
                queryStatementStr.startsWith("CREATE DATABASE") ||
                queryStatementStr.startsWith("USE") ||
                queryStatementStr.startsWith("CREATE INDEX") ||
                queryStatementStr.startsWith("CREATE UNIQUE INDEX") ||
                queryStatementStr.startsWith("DROP INDEX")
            ) {
                // Ignore database statements.
                continue;
            }
            statements.append(queryStatementStr);
            statements.append("\n");
        }
        executor.execute(statements.toString());
        return executor;
    }

    protected ExecutionResult runDbmsExecutor(String selectStr) {
        try {
            globalState.getManager().incrementSelectQueryCount();
            return new ExecutionResult(
                null, ComparatorHelper.getResultSetFirstColumnAsString(selectStr, null, globalState)
            );
        } catch (Exception e) {
            return new ExecutionResult(e, List.of());
        } catch (Error e) {
            return new ExecutionResult(e, List.of());
        }
    }

    protected ExecutionResult runReferenceExecutor(String selectStr) {
        try {
            Table resultTable = this.executor.execute(selectStr);
            List<String> firstColResultSet = new ArrayList<>();
            for (Row row : resultTable.getTableRows()) {
                String result = row.at(0).getValue().getStringValue();
                if (result.equalsIgnoreCase("null")) {
                    // SQLancer treats "null" result values as literal nulls.
                    firstColResultSet.add(null);
                } else {
                    firstColResultSet.add(result);
                }
            }

            return new ExecutionResult(null, firstColResultSet);
        } catch (Exception e) {
            return new ExecutionResult(e, List.of());
        }
    }

    protected void check(String selectQuery) throws Exception {        
        String selectStr = selectQuery
                            // UNKNOWN is unsupported in JSQLParser.
                            .replaceAll(Pattern.quote("UNKNOWN"), "NULL")
                            // DISTINCTROW is unsupported in JSQLParser.
                            .replaceAll(Pattern.quote("DISTINCTROW"), "DISTINCT")
                            // Standardize conventions.
                            .replaceAll(Pattern.quote("! "), "NOT ")
                            .replaceAll(Pattern.quote("||"), "OR")
                            .replaceAll(Pattern.quote("&&"), "AND");

        ExecutionResult dbmsExecutionResult = runDbmsExecutor(selectStr);
        ExecutionResult referenceExecutionResult = runReferenceExecutor(selectStr);

        if (dbmsExecutionResult.throwable != null && referenceExecutionResult.throwable == null) {
            // throw new AssertionError(String.format("MySQL executor reports an exception: %s.", dbmsExecutionResult.throwable));
            return;
        }
        if (dbmsExecutionResult.throwable == null && referenceExecutionResult.throwable != null) {
            System.out.println(selectStr);
            throw new AssertionError(String.format("Reference executor reports an exception: %s.", referenceExecutionResult.throwable));
        }

        ComparatorHelper.assumeResultSetsAreEqual(
            dbmsExecutionResult.firstColResultSet,
            referenceExecutionResult.firstColResultSet,
            selectStr,
            List.of(selectStr),
            globalState
        );
    }
}
