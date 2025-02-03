package sqlancer.postgres.oracle;

import com.sqlengine.dialect.DialectType;
import sqlancer.Randomly;
import sqlancer.common.oracle.ReferenceEngineOracleBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.gen.PostgresRandomQueryGenerator;

public class PostgresReferenceEngineOracle
    extends ReferenceEngineOracleBase<PostgresGlobalState>
    implements TestOracle<PostgresGlobalState> {
    
    private final PostgresGlobalState globalState;

    public PostgresReferenceEngineOracle(PostgresGlobalState globalState) {
        super(globalState, DialectType.POSTGRES);
        this.globalState = globalState;
    }
    
    @Override
    public void check() throws Exception {        
        PostgresSelect selectQuery = PostgresRandomQueryGenerator.createRandomQuery(Randomly.smallNumber() + 1, globalState);
        this.check(selectQuery.asString() + ";");
    }
}
