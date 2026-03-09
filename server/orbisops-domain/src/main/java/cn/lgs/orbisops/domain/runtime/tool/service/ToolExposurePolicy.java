package cn.lgs.orbisops.domain.runtime.tool.service;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;

import java.util.Locale;
import java.util.Set;

/**
 * Global MCP exposure policy. It requires explicit known capabilities; read/write
 * authorization itself belongs to the current Runtime Stage and resource environment.
 */
public final class ToolExposurePolicy {

    private static final Set<String> EXPLICIT_CAPABILITIES = Set.of(
            "read_only", "readonly", "read", "query", "search", "list", "get",
            "evidence", "observe", "inspect",
            "notification", "notify", "notice", "message", "push_report",
            "write", "mutating", "mutate", "update", "create", "delete", "insert",
            "execute", "apply", "deploy", "patch", "restart", "config", "configure", "ddl");

    public boolean allows(String declaredCapability, ToolExposureSettings settings) {
        ToolExposureSettings effective = settings == null
                ? ToolExposureSettings.defaults()
                : settings;
        if (!effective.enforceReadOnlyTools()) return true;
        String capability = normalize(declaredCapability).replace('-', '_');
        return EXPLICIT_CAPABILITIES.contains(capability);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
