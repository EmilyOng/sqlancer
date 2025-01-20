package sqlancer.tidb.oracle;

import java.util.List;
import java.util.regex.Pattern;

import com.sqlengine.dialect.DialectType;
import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.ReferenceEngineOracleBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.tidb.TiDBProvider.TiDBGlobalState;
import sqlancer.tidb.ast.TiDBSelect;
import sqlancer.tidb.gen.TiDBRandomQuerySynthesizer;

public class TiDBReferenceEngineOracle
    extends ReferenceEngineOracleBase<TiDBGlobalState>
    implements TestOracle<TiDBGlobalState> {
    
    private final TiDBGlobalState globalState;

    public TiDBReferenceEngineOracle(TiDBGlobalState globalState) {
        super(globalState, DialectType.TIDB);
        this.globalState = globalState;
    }

    @Override
    public void check() throws Exception {        
        TiDBSelect selectQuery = TiDBRandomQuerySynthesizer.generateSelect(globalState, Randomly.smallNumber() + 1);
        String selectStr = (selectQuery.asString() + ';')
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

        if (dbmsExecutionResult.getThrowable() != null && referenceExecutionResult.getThrowable() == null) {
            // throw new AssertionError(String.format("MySQL executor reports an exception: %s.", dbmsExecutionResult.throwable));
            return;
        }
        if (dbmsExecutionResult.getThrowable() == null && referenceExecutionResult.getThrowable() != null) {
            System.out.println(selectStr);
            throw new AssertionError(String.format("Reference executor reports an exception: %s.", referenceExecutionResult.getThrowable()));
        }

        ComparatorHelper.assumeResultSetsAreEqual(
            dbmsExecutionResult.getFirstColResultSet(),
            referenceExecutionResult.getFirstColResultSet(),
            selectStr,
            List.of(selectStr),
            globalState
        );
    }
}
