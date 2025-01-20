package sqlancer.mysql.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;

public final class MySQLRandomQuerySynthesizer {

    private MySQLRandomQuerySynthesizer() {
    }

    public static MySQLSelect generate(MySQLGlobalState globalState, int nrColumns) {
        MySQLTables tables = globalState.getSchema().getRandomTableNonEmptyTables();
        MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState).setColumns(tables.getColumns());
        MySQLSelect select = new MySQLSelect();

        List<MySQLExpression> allColumns = new ArrayList<>();
        List<MySQLExpression> columnsWithoutAggregations = new ArrayList<>();

        boolean hasGeneratedAggregate = false;

        if (globalState.usesReferenceEngine()) {
            select.setSelectType(Randomly.fromOptions(MySQLSelect.SelectType.valuesReferenceEngine()));
        } else {
            select.setSelectType(Randomly.fromOptions(MySQLSelect.SelectType.values()));
        }

        boolean hasGeneratedNonAggregate = false;
        for (int i = 0; i < nrColumns; i++) {
            if (!hasGeneratedNonAggregate || Randomly.getBoolean()) {
                MySQLExpression expression = gen.generateExpression();
                allColumns.add(expression);
                columnsWithoutAggregations.add(expression);
                hasGeneratedNonAggregate = true;
            } else {
                allColumns.add(gen.generateAggregate());
                hasGeneratedAggregate = true;
            }
        }
        select.setFetchColumns(allColumns);

        List<MySQLExpression> tableList = tables.getTables().stream().map(t -> new MySQLTableReference(t))
                .collect(Collectors.toList());
        select.setFromList(tableList);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression());
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(gen.generateOrderBys());
        }
        if (Randomly.getBoolean()) {
            List<MySQLJoin> joinExpressions = MySQLJoin.getRandomJoinClauses(tables.getTables(), globalState);
            select.setJoinList(joinExpressions.stream().map(join -> (MySQLExpression) join).collect(Collectors.toList()));
        }
        if (hasGeneratedAggregate) {
            select.setGroupByExpressions(columnsWithoutAggregations);
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateHavingClause());
            }
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(MySQLConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            if (!globalState.usesReferenceEngine() && Randomly.getBoolean()) {
                select.setOffsetClause(MySQLConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            }
        }
        return select;
    }

}
