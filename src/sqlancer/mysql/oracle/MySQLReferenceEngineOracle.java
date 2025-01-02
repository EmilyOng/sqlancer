package sqlancer.mysql.oracle;

import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.MySQLGlobalState;

public class MySQLReferenceEngineOracle implements TestOracle<MySQLGlobalState> {
    
    private final MySQLGlobalState globalState;

    public MySQLReferenceEngineOracle(MySQLGlobalState globalState) {
        this.globalState = globalState;
    }

    @Override
    public void check() throws Exception {
        throw new UnsupportedOperationException("MySQL Reference Engine oracle is unimplemented.");
    }
}
