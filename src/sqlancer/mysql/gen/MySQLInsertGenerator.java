package sqlancer.mysql.gen;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLConstant.MySQLNullConstant;
import sqlancer.mysql.MySQLVisitor;

public class MySQLInsertGenerator {

    private static class InsertedValuesLookup {

        private final Map<String, Map<String, Map<String, Set<String>>>> insertedValues;

        public InsertedValuesLookup() {
            this.insertedValues = new HashMap<>();
        }

        public synchronized void insertValue(String database, String table, String column, String value) {
            Map<String, Map<String, Set<String>>> databaseValues = insertedValues
                .getOrDefault(database, new HashMap<>());
            Map<String, Set<String>> tableValues = databaseValues
                .getOrDefault(table, new HashMap<>());
            Set<String> columnValues = tableValues
                .getOrDefault(column, new HashSet<>());
            columnValues.add(value);

            tableValues.put(column, columnValues);
            databaseValues.put(table, tableValues);
            insertedValues.put(database, databaseValues);
        }

        public synchronized boolean containsValue(String database, String table, String column, String value) {
            return insertedValues
                .getOrDefault(database, new HashMap<>())
                .getOrDefault(table, new HashMap<>())
                .getOrDefault(column, new HashSet<>())
                .contains(value);
        }
    }

    private final MySQLTable table;
    private final StringBuilder sb = new StringBuilder();
    private final ExpectedErrors errors = new ExpectedErrors();
    private final MySQLGlobalState globalState;

    private static final InsertedValuesLookup lookup = new InsertedValuesLookup();

    public MySQLInsertGenerator(MySQLGlobalState globalState, MySQLTable table) {
        this.globalState = globalState;
        this.table = table;
    }

    public static SQLQueryAdapter insertRow(MySQLGlobalState globalState) throws SQLException {
        MySQLTable table = globalState.getSchema().getRandomTable();
        return insertRow(globalState, table);
    }

    public static SQLQueryAdapter insertRow(MySQLGlobalState globalState, MySQLTable table) throws SQLException {
        if (globalState.usesReferenceEngine() || Randomly.getBoolean()) {
            return new MySQLInsertGenerator(globalState, table).generateInsert();
        } else {
            return new MySQLInsertGenerator(globalState, table).generateReplace();
        }
    }

    private SQLQueryAdapter generateReplace() {
        sb.append("REPLACE");
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("LOW_PRIORITY", "DELAYED"));
        }
        return generateInto();

    }

    private SQLQueryAdapter generateInsert() {
        sb.append("INSERT");
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("LOW_PRIORITY", "DELAYED", "HIGH_PRIORITY"));
        }
        if (Randomly.getBoolean()) {
            sb.append(" IGNORE");
        }
        return generateInto();
    }

    private boolean isColValueInsertable(MySQLExpression expr, MySQLColumn column) {
        if (column.isPrimaryKey() || column.isUnique()) {
            if (lookup.containsValue(
                globalState.getDatabaseName(),
                table.getName(),
                column.getName(),
                MySQLVisitor.asString(expr)
            )) {
                return false;
            }
            if (column.isPrimaryKey() && expr instanceof MySQLNullConstant) {
                return false;
            }
        } else if (column.isNotNull()) {
            if (expr instanceof MySQLNullConstant) {
                return false;
            }
        }

        return true;
    }

    private String generateValidValueForColumn(MySQLExpressionGenerator gen, MySQLColumn column) {
        MySQLExpression candidateExpr = null;

        boolean hasGenerated = false;
        while (!hasGenerated) {
            candidateExpr = gen.generateConstant();
            if (isColValueInsertable(candidateExpr, column)) {
                hasGenerated = true;
            }
        }

        String candidateValue = MySQLVisitor.asString(candidateExpr);

        lookup.insertValue(
            globalState.getDatabaseName(),
            table.getName(),
            column.getName(),
            candidateValue
        );
        
        return candidateValue;
    }

    private SQLQueryAdapter generateInto() {
        sb.append(" INTO ");
        sb.append(table.getName());
        List<MySQLColumn> columns = table.getRandomNonEmptyColumnSubset()
            .stream().filter(column -> column.hasDefault())
            .collect(Collectors.toList());
        columns.addAll(table.getColumns().stream().filter(column -> !column.hasDefault()).collect(Collectors.toList()));
        sb.append("(");
        sb.append(columns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
        sb.append(") ");
        sb.append("VALUES");
        MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState);
        int nrRows;
        if (Randomly.getBoolean()) {
            nrRows = 1;
        } else {
            nrRows = 1 + Randomly.smallNumber();
        }
        for (int row = 0; row < nrRows; row++) {
            if (row != 0) {
                sb.append(", ");
            }
            sb.append("(");
            for (int c = 0; c < columns.size(); c++) {
                if (c != 0) {
                    sb.append(", ");
                }

                sb.append(generateValidValueForColumn(gen, columns.get(c)));

            }
            sb.append(")");
        }
        MySQLErrors.addInsertUpdateErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors);
    }

}
