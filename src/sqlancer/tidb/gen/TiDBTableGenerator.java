package sqlancer.tidb.gen;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.tidb.TiDBExpressionGenerator;
import sqlancer.tidb.TiDBProvider.TiDBGlobalState;
import sqlancer.tidb.TiDBSchema.TiDBColumn;
import sqlancer.tidb.TiDBSchema.TiDBCompositeDataType;
import sqlancer.tidb.TiDBSchema.TiDBDataType;
import sqlancer.tidb.TiDBSchema.TiDBTable;
import sqlancer.tidb.visitor.TiDBVisitor;

public class TiDBTableGenerator {

    private boolean allowPrimaryKey;
    private final List<TiDBColumn> columns = new ArrayList<>();
    private boolean primaryKeyAsTableConstraints;
    private final ExpectedErrors errors = new ExpectedErrors();

    protected static final Set<String> candidateDefaultValues = new HashSet<>();

    public static SQLQueryAdapter createRandomTableStatement(TiDBGlobalState globalState) throws SQLException {
        if (globalState.getSchema().getDatabaseTables().size() > globalState.getDbmsSpecificOptions().maxNumTables) {
            throw new IgnoreMeException();
        }
        return new TiDBTableGenerator().getQuery(globalState);
    }

    public SQLQueryAdapter getQuery(TiDBGlobalState globalState) throws SQLException {
        errors.add("Information schema is changed during the execution of the statement");
        errors.add("A CLUSTERED INDEX must include all columns in the table's partitioning function");
        String tableName = globalState.getSchema().getFreeTableName();
        int nrColumns = Randomly.smallNumber() + 1;
        allowPrimaryKey = Randomly.getBoolean();
        primaryKeyAsTableConstraints = allowPrimaryKey && Randomly.getBoolean();
        for (int i = 0; i < nrColumns; i++) {
            TiDBColumn fakeColumn = new TiDBColumn("c" + i, null, false, false, false, false);
            columns.add(fakeColumn);
        }
        TiDBExpressionGenerator gen = new TiDBExpressionGenerator(globalState).setColumns(columns);

        StringBuilder sb = new StringBuilder("CREATE TABLE ");
        sb.append(tableName);

        if (!globalState.usesReferenceEngine() && Randomly.getBoolean() && globalState.getSchema().getDatabaseTables().size() > 0) {
            sb.append(" LIKE ");
            TiDBTable otherTable = globalState.getSchema().getRandomTable();
            sb.append(otherTable.getName());
        } else {
            createNewTable(gen, globalState, sb);
        }
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    private void createNewTable(TiDBExpressionGenerator gen, TiDBGlobalState globalState, StringBuilder sb) {
        sb.append("(");
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" ");
            TiDBCompositeDataType type;
            type = TiDBCompositeDataType.getRandom(globalState);
            appendType(sb, type, globalState);
            sb.append(" ");
            boolean isGeneratedColumn = !globalState.usesReferenceEngine() && Randomly.getBooleanWithRatherLowProbability();
            if (isGeneratedColumn) {
                sb.append(" AS (");
                sb.append(TiDBVisitor.asString(gen.generateExpression()));
                sb.append(") ");
                sb.append(Randomly.fromOptions("STORED", "VIRTUAL"));
                sb.append(" ");
                errors.add("Generated column can refer only to generated columns defined prior to it");
                errors.add(
                        "'Defining a virtual generated column as primary key' is not supported for generated columns.");
                errors.add("contains a disallowed function.");
                errors.add("cannot refer to auto-increment column");
            }
            if (!globalState.usesReferenceEngine() && Randomly.getBooleanWithRatherLowProbability()) {
                sb.append("CHECK (");
                sb.append(TiDBVisitor.asString(gen.generateExpression()));
                sb.append(") ");
            }
            if (Randomly.getBooleanWithRatherLowProbability()) {
                sb.append("NOT NULL ");
            }

            boolean hasDefault = false;
            if (Randomly.getBoolean() && type.getPrimitiveDataType().canHaveDefault() && !isGeneratedColumn) {
                hasDefault = true;
                sb.append("DEFAULT ");
                String candidateDefault = TiDBVisitor.asString(gen.generateConstant(type.getPrimitiveDataType()));
                candidateDefaultValues.add(candidateDefault);
                sb.append(candidateDefault);
                sb.append(" ");
                errors.add("Invalid default value");
                errors.add(
                        "All parts of a PRIMARY KEY must be NOT NULL; if you need NULL in a key, use UNIQUE instead");
            }
            if (!globalState.usesReferenceEngine() && type.getPrimitiveDataType() == TiDBDataType.INT && Randomly.getBooleanWithRatherLowProbability()
                    && !isGeneratedColumn) {
                sb.append(" AUTO_INCREMENT ");
                errors.add("there can be only one auto column and it must be defined as a key");
            }
            if (!hasDefault && Randomly.getBooleanWithRatherLowProbability() && canUseAsUnique(type)) {
                sb.append("UNIQUE ");
            }
            if (!hasDefault && Randomly.getBooleanWithRatherLowProbability() && allowPrimaryKey && !primaryKeyAsTableConstraints
                    && canUseAsUnique(type) && !isGeneratedColumn) {
                sb.append("PRIMARY KEY ");
                allowPrimaryKey = false;
            }
        }
        if (primaryKeyAsTableConstraints) {
            sb.append(", PRIMARY KEY(");
            sb.append(
                    Randomly.nonEmptySubset(columns).stream()
                        .filter(c -> !c.isNullable())
                        .map(c -> c.getName()).collect(Collectors.joining(", ")));
            sb.append(")");
            // TODO: do not include blob/text columns here
            errors.add(" used in key specification without a key length");
        }
        sb.append(")");
        if (!globalState.usesReferenceEngine() && Randomly.getBooleanWithRatherLowProbability()) {
            sb.append("PARTITION BY HASH(");
            sb.append(TiDBVisitor.asString(gen.generateExpression()));
            sb.append(") ");
            sb.append("PARTITIONS ");
            sb.append(Randomly.getNotCachedInteger(1, 100));
            errors.add(
                    "Constant, random or timezone-dependent expressions in (sub)partitioning function are not allowed");
            errors.add("This partition function is not allowed");
            errors.add("A PRIMARY KEY must include all columns in the table's partitioning function");
            errors.add("A UNIQUE INDEX must include all columns in the table's partitioning function");
            errors.add("is of a not allowed type for this type of partitioning");
            errors.add("The PARTITION function returns the wrong type");
        }
    }

    private boolean canUseAsUnique(TiDBCompositeDataType type) {
        return type.getPrimitiveDataType() != TiDBDataType.TEXT && type.getPrimitiveDataType() != TiDBDataType.BLOB;
    }

    private void appendType(StringBuilder sb, TiDBCompositeDataType type, TiDBGlobalState globalState) {
        sb.append(type.toString());
        appendSpecifiers(sb, type.getPrimitiveDataType());
        appendSizeSpecifiers(sb, type.getPrimitiveDataType(), globalState);
    }

    private void appendSizeSpecifiers(StringBuilder sb, TiDBDataType type, TiDBGlobalState globalState) {
        if (!globalState.usesReferenceEngine() && type.isNumeric() && Randomly.getBoolean()) {
            sb.append(" UNSIGNED");
        }
        if (!globalState.usesReferenceEngine() && type.isNumeric() && Randomly.getBoolean()) {
            sb.append(" ZEROFILL");
        }
    }

    static void appendSpecifiers(StringBuilder sb, TiDBDataType type) {
        if (type == TiDBDataType.TEXT || type == TiDBDataType.BLOB) {
            sb.append("(");
            sb.append(Randomly.getNotCachedInteger(1, 500));
            sb.append(")");
        }
    }
}
