package sqlancer.mysql.oracle;

import com.sqlengine.dialect.DialectType;
import sqlancer.Randomly;
import sqlancer.common.oracle.ReferenceEngineOracleBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.gen.MySQLRandomQuerySynthesizer;

public class MySQLReferenceEngineOracle
    extends ReferenceEngineOracleBase<MySQLGlobalState>
    implements TestOracle<MySQLGlobalState> {
    
    private final MySQLGlobalState globalState;

    public MySQLReferenceEngineOracle(MySQLGlobalState globalState) {
        super(globalState, DialectType.MYSQL);
        this.globalState = globalState;
    }
    
    @Override
    public void check() throws Exception {        
        MySQLSelect selectQuery = MySQLRandomQuerySynthesizer.generate(globalState, Randomly.smallNumber() + 1);
        this.check(selectQuery.asString() + ";");
    }
}
