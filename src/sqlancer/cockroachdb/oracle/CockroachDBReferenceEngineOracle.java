package sqlancer.cockroachdb.oracle;

import com.sqlengine.dialect.DialectType;

import sqlancer.Randomly;
import sqlancer.cockroachdb.CockroachDBProvider.CockroachDBGlobalState;
import sqlancer.cockroachdb.ast.CockroachDBSelect;
import sqlancer.cockroachdb.gen.CockroachDBRandomQuerySynthesizer;
import sqlancer.common.oracle.ReferenceEngineOracleBase;
import sqlancer.common.oracle.TestOracle;

public class CockroachDBReferenceEngineOracle
    extends ReferenceEngineOracleBase<CockroachDBGlobalState>
    implements TestOracle<CockroachDBGlobalState> {
    
    private final CockroachDBGlobalState globalState;

    public CockroachDBReferenceEngineOracle(CockroachDBGlobalState globalState) {
        super(globalState, DialectType.COCKROACHDB);
        this.globalState = globalState;
    }

    @Override
    public void check() throws Exception {        
        CockroachDBSelect selectQuery = CockroachDBRandomQuerySynthesizer.generateSelect(globalState, Randomly.smallNumber() + 1);
        this.check(selectQuery.asString() + ";");
    }
}
