package cn.lgs.orbisops.domain.changepackage.model;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;

public final class ChangePackageCurrentState {

    private final EnumMap<ChangePackageCurrentField, String> values;

    private ChangePackageCurrentState(EnumMap<ChangePackageCurrentField, String> values) {
        this.values = values;
    }

    public static ChangePackageCurrentState fromSnapshot(Map<String, ?> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_STATE_REQUIRED");
        }
        EnumMap<ChangePackageCurrentField, String> values = new EnumMap<>(ChangePackageCurrentField.class);
        for (ChangePackageCurrentField field : ChangePackageCurrentField.values()) {
            String value = text(snapshot.get(field.snapshotKey()));
            if (value.isBlank() && field.defaultValue() != null) {
                value = field.defaultValue();
            }
            values.put(field, value);
        }
        require(values, ChangePackageCurrentField.RISK_LEVEL, "CHANGE_PACKAGE_CURRENT_RISK_REQUIRED");
        return new ChangePackageCurrentState(values);
    }

    public static ChangePackageCurrentState fromPersistentValues(Map<ChangePackageCurrentField, ?> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_STATE_REQUIRED");
        }
        EnumMap<ChangePackageCurrentField, String> values = new EnumMap<>(ChangePackageCurrentField.class);
        for (ChangePackageCurrentField field : ChangePackageCurrentField.values()) {
            String value = text(source.get(field));
            if (value.isBlank() && field.defaultValue() != null) value = field.defaultValue();
            values.put(field, value);
        }
        require(values, ChangePackageCurrentField.RISK_LEVEL, "CHANGE_PACKAGE_CURRENT_RISK_REQUIRED");
        return new ChangePackageCurrentState(values);
    }

    public Map<ChangePackageCurrentField, String> values() {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }

    public String value(ChangePackageCurrentField field) {
        ChangePackageCurrentField requiredField = requireField(field);
        if (requiredField.nullable()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_FIELD_IS_NULLABLE:" + requiredField.name());
        }
        return values.getOrDefault(requiredField, requiredField.defaultValue());
    }

    public String nullable(ChangePackageCurrentField field) {
        ChangePackageCurrentField requiredField = requireField(field);
        if (!requiredField.nullable()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_FIELD_IS_NOT_NULLABLE:" + requiredField.name());
        }
        String value = values.getOrDefault(requiredField, "");
        return value.isBlank() ? null : value;
    }

    private static ChangePackageCurrentField requireField(ChangePackageCurrentField field) {
        if (field == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_FIELD_REQUIRED");
        return field;
    }

    private static void require(EnumMap<ChangePackageCurrentField, String> values,
                                ChangePackageCurrentField field,
                                String reasonCode) {
        if (values.getOrDefault(field, "").isBlank()) throw new IllegalArgumentException(reasonCode);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
