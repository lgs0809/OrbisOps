package cn.lgs.orbisops.trigger.ops.toolset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explicit policy for direct Local access to systems outside Agent Station ownership.
 * Production profile must disable every external Local provider and use MCP instead.
 */
@Component
public final class OpsExternalLocalProviderSettings {

    private static final Set<String> ARBITRARY_SQL_TOOLSETS = Set.of(
            "db.mysql.change",
            "cache.redis.change");
    private static final Set<String> ARBITRARY_COMMAND_TOOLSETS = Set.of(
            "infra.k8s.remediation",
            "job.platform.execute");

    public enum Mode {
        DEVELOPMENT,
        TEST,
        PRODUCTION;

        static Mode parse(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank()) return DEVELOPMENT;
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("TOOL_RUNTIME_PROFILE_INVALID：" + value);
            }
        }
    }

    private final Mode mode;
    private final boolean prometheusEnabled;
    private final boolean elasticsearchEnabled;
    private final boolean mysqlEnabled;
    private final boolean redisEnabled;
    private final boolean dockerEnabled;
    private final boolean forbidArbitrarySql;
    private final boolean forbidArbitraryShell;
    private final boolean forbidWildcardRollout;

    public OpsExternalLocalProviderSettings(
            @Value("${orbisops.tool-runtime.mode:DEVELOPMENT}") String mode,
            @Value("${orbisops.external-local.prometheus-enabled:true}") boolean prometheusEnabled,
            @Value("${orbisops.external-local.elasticsearch-enabled:true}") boolean elasticsearchEnabled,
            @Value("${orbisops.external-local.mysql-enabled:true}") boolean mysqlEnabled,
            @Value("${orbisops.external-local.redis-enabled:true}") boolean redisEnabled,
            @Value("${orbisops.external-local.docker-enabled:true}") boolean dockerEnabled,
            @Value("${orbisops.tool-runtime.forbid-arbitrary-sql:false}") boolean forbidArbitrarySql,
            @Value("${orbisops.tool-runtime.forbid-arbitrary-shell:false}") boolean forbidArbitraryShell,
            @Value("${orbisops.tool-runtime.forbid-wildcard-rollout:false}") boolean forbidWildcardRollout) {
        this.mode = Mode.parse(mode);
        this.prometheusEnabled = prometheusEnabled;
        this.elasticsearchEnabled = elasticsearchEnabled;
        this.mysqlEnabled = mysqlEnabled;
        this.redisEnabled = redisEnabled;
        this.dockerEnabled = dockerEnabled;
        this.forbidArbitrarySql = forbidArbitrarySql;
        this.forbidArbitraryShell = forbidArbitraryShell;
        this.forbidWildcardRollout = forbidWildcardRollout;
    }

    public static OpsExternalLocalProviderSettings compatibilityEnabled() {
        return new OpsExternalLocalProviderSettings(
                "DEVELOPMENT", true, true, true, true, true,
                false, false, false);
    }

    public static OpsExternalLocalProviderSettings productionDisabled() {
        return new OpsExternalLocalProviderSettings(
                "PRODUCTION", false, false, false, false, false,
                true, true, true);
    }

    public Mode mode() {
        return mode;
    }

    public boolean production() {
        return mode == Mode.PRODUCTION;
    }

    public boolean allowsAdapter(String adapterType) {
        return switch (normalize(adapterType)) {
            case "LOCAL_PROMETHEUS" -> prometheusEnabled;
            case "LOCAL_ELASTICSEARCH" -> elasticsearchEnabled;
            case "LOCAL_MYSQL" -> mysqlEnabled;
            case "LOCAL_REDIS" -> redisEnabled;
            case "LOCAL_DOCKER" -> dockerEnabled;
            default -> true;
        };
    }

    public boolean allowsToolset(OpsToolsetDefinition toolset) {
        if (toolset == null || !allowsAdapter(toolset.getAdapterType())) return false;
        if (!production()) return true;
        String toolsetId = normalizeId(toolset.getToolsetId());
        if (forbidArbitrarySql && ARBITRARY_SQL_TOOLSETS.contains(toolsetId)) return false;
        return !forbidArbitraryShell || !ARBITRARY_COMMAND_TOOLSETS.contains(toolsetId);
    }

    public boolean productionSafe() {
        return !production()
                || (!prometheusEnabled
                && !elasticsearchEnabled
                && !mysqlEnabled
                && !redisEnabled
                && !dockerEnabled
                && forbidArbitrarySql
                && forbidArbitraryShell
                && forbidWildcardRollout);
    }

    public Map<String, Object> readiness() {
        boolean up = productionSafe();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", up ? "UP" : "DOWN");
        details.put("reason", up ? "" : "PRODUCTION_TOOL_PROFILE_UNSAFE");
        details.put("mode", mode.name());
        details.put("prometheusEnabled", prometheusEnabled);
        details.put("elasticsearchEnabled", elasticsearchEnabled);
        details.put("mysqlEnabled", mysqlEnabled);
        details.put("redisEnabled", redisEnabled);
        details.put("dockerEnabled", dockerEnabled);
        details.put("forbidArbitrarySql", forbidArbitrarySql);
        details.put("forbidArbitraryShell", forbidArbitraryShell);
        details.put("forbidWildcardRollout", forbidWildcardRollout);
        details.put("externalResourcesRequireMcp", production());
        return Map.copyOf(details);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
