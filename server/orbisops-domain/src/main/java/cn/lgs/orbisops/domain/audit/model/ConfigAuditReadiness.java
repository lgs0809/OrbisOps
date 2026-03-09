package cn.lgs.orbisops.domain.audit.model;

public record ConfigAuditReadiness(
        String store,
        String status,
        boolean autoInit,
        boolean memoryFallbackAllowed,
        String reason) {

    public ConfigAuditReadiness {
        store = value(store);
        status = value(status);
        reason = value(reason);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
