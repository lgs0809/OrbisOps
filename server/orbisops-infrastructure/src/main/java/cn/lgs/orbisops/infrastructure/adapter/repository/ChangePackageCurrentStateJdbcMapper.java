package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared JDBC ordering and row mapping for the flattened ChangePackage current state. */
final class ChangePackageCurrentStateJdbcMapper {

    private static final List<ChangePackageCurrentField> ORDERED_FIELDS =
            List.of(ChangePackageCurrentField.values());
    private static final String COLUMN_LIST = buildColumnList();
    private static final String ASSIGNMENT_LIST = buildAssignmentList();

    private ChangePackageCurrentStateJdbcMapper() {
    }

    static int fieldCount() {
        return ORDERED_FIELDS.size();
    }

    static String columnList() {
        return COLUMN_LIST;
    }

    static String assignmentList() {
        return ASSIGNMENT_LIST;
    }

    static String placeholders(int count) {
        if (count <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_PLACEHOLDER_COUNT_INVALID");
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    static List<Object> orderedValues(ChangePackageCurrentState state) {
        if (state == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_STATE_REQUIRED");
        List<Object> values = new ArrayList<>(ORDERED_FIELDS.size());
        for (ChangePackageCurrentField field : ORDERED_FIELDS) {
            values.add(field.nullable() ? state.nullable(field) : state.value(field));
        }
        return Collections.unmodifiableList(values);
    }

    static ChangePackageCurrentState fromRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_ROW_REQUIRED");
        }
        EnumMap<ChangePackageCurrentField, Object> values = new EnumMap<>(ChangePackageCurrentField.class);
        for (ChangePackageCurrentField field : ORDERED_FIELDS) {
            String column = columnName(field.snapshotKey());
            values.put(field, row.containsKey(column) ? row.get(column) : row.get(field.snapshotKey()));
        }
        return ChangePackageCurrentState.fromPersistentValues(values);
    }

    private static String buildColumnList() {
        List<String> columns = new ArrayList<>(ORDERED_FIELDS.size());
        for (ChangePackageCurrentField field : ORDERED_FIELDS) {
            columns.add(columnName(field.snapshotKey()));
        }
        return String.join(", ", columns);
    }

    private static String buildAssignmentList() {
        List<String> assignments = new ArrayList<>(ORDERED_FIELDS.size());
        for (ChangePackageCurrentField field : ORDERED_FIELDS) {
            assignments.add(columnName(field.snapshotKey()) + "=?");
        }
        return String.join(", ", assignments);
    }

    static String columnName(String snapshotKey) {
        String normalized = snapshotKey == null ? "" : snapshotKey.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("CHANGE_PACKAGE_STATE_KEY_REQUIRED");
        return normalized.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
