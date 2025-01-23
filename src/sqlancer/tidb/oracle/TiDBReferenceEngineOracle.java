package sqlancer.tidb.oracle;

import com.sqlengine.dialect.DialectType;
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
        this.check(selectQuery.asString() + ";");
    }
}
