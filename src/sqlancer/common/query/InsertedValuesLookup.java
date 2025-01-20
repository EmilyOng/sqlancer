package sqlancer.common.query;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InsertedValuesLookup {
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
