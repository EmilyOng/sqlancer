package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTables;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.ForClause;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresSelect.SelectType;

public final class PostgresRandomQueryGenerator {

    private PostgresRandomQueryGenerator() {
    }

    public static PostgresSelect createRandomQuery(int nrColumns, PostgresGlobalState globalState) {
        PostgresTables tables = globalState.getSchema().getRandomTableNonEmptyTables();
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState).setColumns(tables.getColumns());
        List<PostgresExpression> allColumns = new ArrayList<>();
        List<PostgresExpression> columnsWithoutAggregations = new ArrayList<>();

        boolean hasGeneratedAggregate = false;
        boolean hasGeneratedNonAggregate = false;
        for (int i = 0; i < nrColumns; i++) {
            if (!hasGeneratedNonAggregate || Randomly.getBoolean()) {
                PostgresExpression expression = gen.generateExpression(0);
                allColumns.add(expression);
                columnsWithoutAggregations.add(expression);
                hasGeneratedNonAggregate = true;
            } else {
                allColumns.add(gen.generateAggregate());
                hasGeneratedAggregate = true;
            }
        }

        PostgresSelect select = new PostgresSelect();
        select.setSelectType(SelectType.getRandom());
        if (!globalState.usesReferenceEngine() && select.getSelectOption() == SelectType.DISTINCT && Randomly.getBoolean()) {
            select.setDistinctOnClause(gen.generateExpression(0));
        }
        select.setFromList(tables.getTables().stream().map(t -> new PostgresFromTable(t, !globalState.usesReferenceEngine() && Randomly.getBoolean()))
                .collect(Collectors.toList()));
        select.setFetchColumns(allColumns);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression(0, PostgresDataType.BOOLEAN));
        }
        if (hasGeneratedAggregate) {
            select.setGroupByExpressions(columnsWithoutAggregations);
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateHavingClause());
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(gen.generateOrderBys());
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            if (!globalState.usesReferenceEngine() && Randomly.getBoolean()) {
                select.setOffsetClause(
                        PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setForClause(ForClause.getRandom());
        }
        return select;
    }

}
