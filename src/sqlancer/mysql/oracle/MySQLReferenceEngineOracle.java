package sqlancer.mysql.oracle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sqlengine.dialect.DialectFactory;
import com.sqlengine.dialect.DialectManager;
import com.sqlengine.dialect.DialectType;
import com.sqlengine.engine.Executor;
import com.sqlengine.env.Row;
import com.sqlengine.env.Table;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.gen.MySQLRandomQuerySynthesizer;

public class MySQLReferenceEngineOracle implements TestOracle<MySQLGlobalState> {
    
    private class ExecutionResult {
        private final Exception exception;
        private final List<String> firstColResultSet;

        public ExecutionResult(Exception exception, List<String> firstColResultSet) {
            this.exception = exception;
            this.firstColResultSet = firstColResultSet;
        }
    }
    
    private final MySQLGlobalState globalState;
    private final DialectManager dialectManager;

    public MySQLReferenceEngineOracle(MySQLGlobalState globalState) {
        this.globalState = globalState;
        this.dialectManager = DialectFactory.createDialectManager(DialectType.MYSQL);
    }

    private ExecutionResult runDbmsExecutor(String selectStr) {
        try {
            globalState.executeStatement(new SQLQueryAdapter(selectStr));
            globalState.getManager().incrementSelectQueryCount();

            return new ExecutionResult(
                null, ComparatorHelper.getResultSetFirstColumnAsString(selectStr, null, globalState)
            );
        } catch (Exception e) {
            return new ExecutionResult(e, List.of());
        }
    }

    private ExecutionResult runReferenceExecutor(String selectStr) {
        Executor executor = new Executor(dialectManager);

        StringBuilder statements = new StringBuilder();
        for (Query<?> queryStatement : globalState.getState().getStatements()) {
            String queryStatementStr = queryStatement.toString();
            if (
                queryStatementStr.startsWith("DROP DATABASE") ||
                queryStatementStr.startsWith("CREATE DATABASE") ||
                queryStatementStr.startsWith("USE")
            ) {
                // Ignore database statements.
                continue;
            }
            statements.append(queryStatementStr);
            statements.append("\n");
        }
        statements.append(selectStr);

        try {
            Table resultTable = executor.execute(statements.toString());
            List<String> firstColResultSet = new ArrayList<>();
            for (Iterator<Row> rowsIterator = resultTable.getTableRows(); rowsIterator.hasNext();) {
                Row row = rowsIterator.next();
                firstColResultSet.add(row.at(0).getValue().getStringValue());
            }

            return new ExecutionResult(null, firstColResultSet);
        } catch (Exception e) {
            return new ExecutionResult(e, List.of());
        }
    }

    @Override
    public void check() throws Exception {        
        String selectStr = MySQLVisitor.asString(MySQLRandomQuerySynthesizer.generate(globalState, Randomly.smallNumber() + 1)) + ';';
        ExecutionResult dbmsExecutionResult = runDbmsExecutor(selectStr);
        ExecutionResult referenceExecutionResult = runReferenceExecutor(selectStr);

        if (dbmsExecutionResult.exception != null && referenceExecutionResult.exception == null) {
            throw new AssertionError(String.format("MySQL executor reports an exception: %s.", dbmsExecutionResult.exception));
        }
        if (dbmsExecutionResult.exception == null && referenceExecutionResult.exception != null) {
            throw new AssertionError(String.format("Reference executor reports an exception: %s.", referenceExecutionResult.exception));
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
