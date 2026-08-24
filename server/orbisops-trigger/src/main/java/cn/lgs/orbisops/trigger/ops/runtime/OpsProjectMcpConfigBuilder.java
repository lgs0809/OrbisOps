package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.intValue;
import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.map;
import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.stringList;
import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.text;

/** Builds internal and external MCP runtime DTOs after credential handling. */
final class OpsProjectMcpConfigBuilder {

    private final OpsProjectMcpTypePolicy typePolicy;
    private final OpsProjectMcpScriptLocator scriptLocator;
    private final OpsProjectMcpEnvironmentProjector environmentProjector;

    OpsProjectMcpConfigBuilder(
            OpsProjectMcpTypePolicy typePolicy,
            OpsProjectMcpScriptLocator scriptLocator,
            OpsProjectMcpEnvironmentProjector environmentProjector) {
        if (typePolicy == null
                || scriptLocator == null
                || environmentProjector == null) {
            throw new IllegalArgumentException(
                    "PROJECT_MCP_CONFIG_BUILDER_DEPENDENCY_REQUIRED");
        }
        this.typePolicy = typePolicy;
        this.scriptLocator = scriptLocator;
        this.environmentProjector = environmentProjector;
    }

    OpsMcpServerConfig buildInternal(
            Map<String, Object> mcp,
            Map<String, Object> resource,
            Map<String, Object> resolvedCredential) {
        Map<String, Object> generated = mcp == null ? Map.of() : mcp;
        Map<String, Object> source = resource == null ? Map.of() : resource;
        String type = typePolicy.normalize(text(
                source.get("type"),
                text(generated.get("resourceType"), "")));
        String mcpId = text(
                generated.get("mcpId"),
                text(source.get("resourceId"), "project-mcp"));
        String endpoint = text(
                source.get("endpoint"),
                typePolicy.defaultEndpoint(type));
        Map<String, Object> permission = typePolicy.enrichPermission(
                type,
                map(source.get("permission")));
        Map<String, String> governance = governance(generated, source, permission);
        OpsMcpServerConfig.OpsMcpServerConfigBuilder builder =
                OpsMcpServerConfig.builder()
                        .name(mcpId)
                        .projectId(text(
                                generated.get("projectId"),
                                text(source.get("projectId"), "")))
                        .mcpId(mcpId)
                        .toolId(text(generated.get("toolId"), mcpId))
                        .progressiveManaged(true)
                        .description(text(generated.get("mcpName"), mcpId))
                        .transport("stdio")
                        .command("node")
                        .timeoutSeconds(typePolicy.timeoutSeconds(type))
                        .toolCapabilities(governance)
                        .allowedTools(typePolicy.allowedTools(type));
        String scriptArgument = scriptArgument(type);
        if (!scriptArgument.isBlank()) {
            Map<String, String> environment = new LinkedHashMap<>(
                    environmentProjector.project(
                            type,
                            endpoint,
                            resolvedCredential,
                            permission));
            governance.forEach((key, value) -> environment.put(
                    "OPS_" + key.replaceAll("([a-z])([A-Z])", "$1_$2")
                            .toUpperCase(Locale.ROOT),
                    value));
            builder.args(List.of(scriptArgument)).env(environment);
        } else {
            builder.args(List.of());
        }
        return builder.build();
    }

    OpsMcpServerConfig buildExternal(
            Map<String, Object> mcp,
            Map<String, String> headers) {
        Map<String, Object> generated = mcp == null ? Map.of() : mcp;
        Map<String, Object> transportConfig = map(
                generated.get("transportConfig"));
        String mcpId = text(generated.get("mcpId"), "external-mcp");
        return OpsMcpServerConfig.builder()
                .name(mcpId)
                .description(text(generated.get("mcpName"), "External MCP"))
                .projectId(text(generated.get("projectId"), ""))
                .mcpId(text(generated.get("mcpId"), ""))
                .toolId(text(
                        generated.get("toolId"),
                        text(generated.get("mcpId"), "")))
                .progressiveManaged(true)
                .transport(text(
                        generated.get("transportType"),
                        "streamable-http"))
                .url(text(transportConfig.get("endpoint"), ""))
                .headers(headers == null ? Map.of() : headers)
                .toolCapabilities(Map.of(
                        "resourceEnvironment", "external",
                        "permissionProfile", "DATA_READONLY",
                        // Connection availability is distinct from per-tool execution authority.
                        // Progressive tools still require a reviewed policy and approved LANDING binding.
                        "allowedStages", "INVESTIGATE,PREPARE,LANDING",
                        "readOnly", "true",
                        "riskLevel", "HIGH",
                        "effectCeiling", "READ_ONLY",
                        "terminalPolicyRef", "terminal/external-readonly",
                        "allowedRoots", ""))
                .timeoutSeconds(intValue(
                        generated.get("requestTimeout"),
                        30))
                .allowedTools(stringList(
                        generated.get("allowedActions"),
                        List.of()))
                .blockedTools(stringList(
                        generated.get("blockedActions"),
                        List.of()))
                .build();
    }

    private Map<String, String> governance(
            Map<String, Object> mcp,
            Map<String, Object> resource,
            Map<String, Object> permission) {
        Map<String, Object> definition = mcp == null ? Map.of() : mcp;
        String environment = text(resource.get("environment"), "prod").toLowerCase(Locale.ROOT);
        boolean production = "prod".equals(environment) || "production".equals(environment);
        String readOnly = text(
                definition.get("readOnly"),
                text(permission.get("readOnly"), production ? "false" : "false"));
        boolean productionReadOnly = production && Boolean.parseBoolean(readOnly);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("platformGenerated", "true");
        values.put("resourceEnvironment", environment);
        values.put("permissionProfile", text(
                permission.get("permissionProfile"),
                productionReadOnly
                        ? "DATA_READONLY"
                        : production ? "PROD_FULL" : "TEST_FULL"));
        values.put("allowedStages", String.join(",", stringList(
                permission.get("allowedStages"),
                productionReadOnly
                        ? List.of("INVESTIGATE", "PREPARE", "LANDING")
                        : production
                                ? List.of("INVESTIGATE", "LANDING")
                                : List.of("INVESTIGATE", "PREPARE", "LANDING"))));
        values.put("readOnly", readOnly);
        values.put("riskLevel", text(
                definition.get("riskLevel"),
                text(permission.get("riskLevel"), production ? "HIGH" : "MEDIUM")));
        values.put("effectCeiling", text(
                permission.get("effectCeiling"),
                productionReadOnly ? "READ_ONLY" : production ? "WRITE" : "TEST_WRITE"));
        values.put("terminalPolicyRef", text(
                permission.get("terminalPolicyRef"),
                productionReadOnly ? "terminal/prod-diagnostic" : production ? "terminal/prod-full" : "terminal/test-full"));
        values.put("allowedRoots", String.join(",", stringList(
                permission.get("allowedRoots"),
                List.of())));
        return Map.copyOf(values);
    }

    private String scriptArgument(String type) {
        return switch (typePolicy.normalize(type)) {
            case "mysql" -> environmentScript(
                    "MYSQL_MCP_SCRIPT",
                    "mysql-readonly-mcp-server.mjs");
            case "postgresql" -> environmentScript(
                    "POSTGRES_MCP_SCRIPT",
                    "postgresql-readonly-mcp-server.mjs");
            case "redis" -> environmentScript(
                    "REDIS_MCP_SCRIPT",
                    "redis-readonly-mcp-server.mjs");
            case "rabbitmq" -> environmentScript(
                    "RABBITMQ_MCP_SCRIPT",
                    "rabbitmq-readonly-mcp-server.mjs");
            case "elasticsearch" -> environmentScript(
                    "ES_MCP_SCRIPT",
                    "elasticsearch-mcp-server.mjs");
            case "prometheus" -> environmentScript(
                    "PROMETHEUS_MCP_SCRIPT",
                    "prometheus-mcp-server.mjs");
            case "openapi" -> environmentScript(
                    "OPENAPI_MCP_SCRIPT",
                    "openapi-mcp-server.mjs");
            case "service_control" -> environmentScript(
                    "SERVICE_CONTROL_MCP_SCRIPT",
                    "service-control-mcp-server.mjs");
            default -> "";
        };
    }

    private String environmentScript(String variable, String fileName) {
        return "${env:" + variable + ":" + scriptLocator.locate(fileName) + "}";
    }
}
