package sqlancer.cockroachdb.oracle;

import com.sqlengine.dialect.DialectType;

import sqlancer.Randomly;
import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.cockroachdb.ast.CockroachDBSelect;
import sqlancer.cockroachdb.gen.CockroachDBRandomQuerySynthesizer;
import sqlancer.common.oracle.ReferenceEngineOracleBase;
import sqlancer.common.oracle.TestOracle;

public class CockroachDBReferenceEngineOracle
    extends ReferenceEngineOracleBase<CockroachDBProvider.CockroachDBGlobalState>
    implements TestOracle<CockroachDBProvider.CockroachDBGlobalState> {
    
    private final CockroachDBProvider.CockroachDBGlobalState globalState;

    public CockroachDBReferenceEngineOracle(CockroachDBProvider.CockroachDBGlobalState globalState) {
        super(globalState, DialectType.COCKROACHDB);
        this.globalState = globalState;
    }

    @Override
    public void check() throws Exception {        
        CockroachDBSelect selectQuery = CockroachDBRandomQuerySynthesizer.generateSelect(globalState, Randomly.smallNumber() + 1);
        this.check(selectQuery.asString() + ";");
    }
}
