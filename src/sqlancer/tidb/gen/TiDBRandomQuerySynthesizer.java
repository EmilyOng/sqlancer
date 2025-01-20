package sqlancer.tidb.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.tidb.TiDBExpressionGenerator;
import sqlancer.tidb.TiDBProvider.TiDBGlobalState;
import sqlancer.tidb.TiDBSchema.TiDBTables;
import sqlancer.tidb.ast.TiDBExpression;
import sqlancer.tidb.ast.TiDBJoin;
import sqlancer.tidb.ast.TiDBSelect;
import sqlancer.tidb.ast.TiDBTableReference;
import sqlancer.tidb.visitor.TiDBVisitor;

public final class TiDBRandomQuerySynthesizer {

    private TiDBRandomQuerySynthesizer() {
    }

    public static SQLQueryAdapter generate(TiDBGlobalState globalState, int nrColumns) {
        TiDBSelect select = generateSelect(globalState, nrColumns);
        return new SQLQueryAdapter(TiDBVisitor.asString(select));
    }

    public static TiDBSelect generateSelect(TiDBGlobalState globalState, int nrColumns) {
        TiDBTables tables = globalState.getSchema().getRandomTableNonEmptyTables();
        TiDBExpressionGenerator gen = new TiDBExpressionGenerator(globalState).setColumns(tables.getColumns());
        // Generate aggregates separately.
        gen.setAllowAggregates(false);
        TiDBSelect select = new TiDBSelect();

        // select.setDistinct(Randomly.getBoolean());
        
        List<TiDBExpression> allColumns = new ArrayList<>();
        List<TiDBExpression> columnsWithoutAggregations = new ArrayList<>();

        boolean hasGeneratedAggregate = false;
        boolean hasGeneratedNonAggregate = false;
        for (int i = 0; i < nrColumns; i++) {
            if (!hasGeneratedNonAggregate || Randomly.getBoolean()) {
                TiDBExpression expression = gen.generateExpression();
                allColumns.add(expression);
                columnsWithoutAggregations.add(expression);
                hasGeneratedNonAggregate = true;
            } else {
                allColumns.add(gen.generateAggregate());
                hasGeneratedAggregate = true;
            }
        }

        select.setFetchColumns(allColumns);
        List<TiDBExpression> tableList = tables.getTables().stream().map(t -> new TiDBTableReference(t))
                .collect(Collectors.toList());

        select.setFromList(tableList);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression());
        }
        if (Randomly.getBoolean()) {
            List<TiDBJoin> joinExpressions = TiDBJoin.getJoins(tableList, globalState);
            select.setJoinList(joinExpressions.stream().map(join -> (TiDBExpression) join).collect(Collectors.toList()));
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(gen.generateOrderBys());
        }
        if (hasGeneratedAggregate) {
            select.setGroupByExpressions(columnsWithoutAggregations);
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateHavingClause());
            }
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(gen.generateExpression());
        }
        if (!globalState.usesReferenceEngine() && Randomly.getBoolean()) {
            select.setOffsetClause(gen.generateExpression());
        }
        return select;
    }

}
