package cn.lgs.orbisops.domain.audit.service;

import java.util.Locale;

public final class ConfigAuditRiskPolicy {

    public String resolve(String module, String action, String configuredRiskLevel) {
        String configured = value(configuredRiskLevel).toUpperCase(Locale.ROOT);
        if (!configured.isBlank()) return configured;

        String key = (value(module) + " " + value(action)).toLowerCase(Locale.ROOT);
        if (key.matches(".*(execute|rollback|delete|disable|approve|reject|permission|role|credential).*")) {
            return "HIGH";
        }
        if (key.matches(".*(publish|update|create|copy|generate|status|dry-run).*")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
