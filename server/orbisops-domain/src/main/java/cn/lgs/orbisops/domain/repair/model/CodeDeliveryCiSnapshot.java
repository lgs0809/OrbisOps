package cn.lgs.orbisops.domain.repair.model;

public record CodeDeliveryCiSnapshot(String status, String url) {

    public CodeDeliveryCiSnapshot {
        status = required(status, "CODE_DELIVERY_CI_STATUS_REQUIRED").toUpperCase();
        url = value(url);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
