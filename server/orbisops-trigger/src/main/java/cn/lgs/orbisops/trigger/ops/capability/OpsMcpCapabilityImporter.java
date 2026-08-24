package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.mcp.McpAuthoritativeToolDefinition;
import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.project.ProjectExternalMcpApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeHashing;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** MCP endpoint registration, remote discovery, hydration, and persistence boundary. */
final class OpsMcpCapabilityImporter {

    private final OpsCapabilityImportUrlPolicy urlPolicy;
    private final ProjectExternalMcpApplicationService externalMcpService;
    private final OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService;
    private final OpsMcpToolProvider mcpToolProvider;
    private final ProgressiveMcpProcessManager progressiveMcpProcessManager;

    OpsMcpCapabilityImporter(
            OpsCapabilityImportUrlPolicy urlPolicy,
            ProjectExternalMcpApplicationService externalMcpService,
            OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService,
            OpsMcpToolProvider mcpToolProvider,
            ProgressiveMcpProcessManager progressiveMcpProcessManager) {
        this.urlPolicy = urlPolicy;
        this.externalMcpService = externalMcpService;
        this.projectMcpRuntimeConfigService = projectMcpRuntimeConfigService;
        this.mcpToolProvider = mcpToolProvider;
        this.progressiveMcpProcessManager = progressiveMcpProcessManager;
    }

    Result importCapability(Input input) {
        URI endpoint = urlPolicy.validate(input.sourceUrl());
        String name = firstText(
                text(input.capabilityName()),
                endpoint.getHost() + " MCP");
        String credentialRef = text(input.credentialRef());
        String transport = text(input.transportType());
        if (!StringUtils.hasText(transport)) {
            transport = endpoint.getPath() != null
                    && endpoint.getPath()
                            .toLowerCase(Locale.ROOT)
                            .contains("sse")
                    ? "sse"
                    : "streamable-http";
        }
        String endpointHash = OpsRuntimeHashing.canonicalHash(Map.of(
                "projectId", input.projectId(),
                "endpoint", endpoint.toString()));
        String mcpId = slug(name) + "-" + endpointHash.substring(0, 8);
        ProjectExternalMcpApplicationService externalService =
                requireExternalMcpService();
        Map<String, Object> mcp = externalService.register(
                input.projectId(),
                mcpId,
                name,
                endpoint.toString(),
                transport,
                credentialRef,
                StringUtils.hasText(credentialRef) ? "BEARER" : "NONE");
        String storedMcpId = firstText(text(mcp.get("mcpId")), mcpId);
        List<Map<String, Object>> definitions;
        List<Map<String, Object>> suggestions = new ArrayList<>();
        String discoveryStatus = "DISCOVERED_PENDING_REVIEW";
        String discoveryError = "";
        try {
            if (mcpToolProvider == null
                    || progressiveMcpProcessManager == null) {
                throw new IllegalStateException(
                        "MCP_DISCOVERY_SERVICE_NOT_CONFIGURED");
            }
            OpsMcpServerConfig server = requireProjectMcpRuntimeConfigService()
                    .resolveForDiscovery(input.projectId(), storedMcpId)
                    .orElseThrow(() -> new IllegalStateException(
                            "MCP_DISCOVERY_CONFIG_NOT_FOUND"));
            var inspectedDefinitions = mcpToolProvider.inspectRemoteToolDefinitions(server);
            definitions = OpsNodeDeadlineContext.withTimeout(20, () -> {
                List<Map<String, Object>> discoveredViews = new ArrayList<>();
                for (Map<String, Object> definition : inspectedDefinitions) {
                    Map<String, Object> hydrated = progressiveMcpProcessManager
                            .hydrateSchema(new McpSchemaHydrationRequest(
                                    input.projectId(),
                                    storedMcpId,
                                    require(
                                            text(definition.get("toolName")),
                                            "MCP_REMOTE_TOOL_NAME_REQUIRED"),
                                    "",
                                    "",
                                    authoritativeDefinition(definition)))
                            .view();
                    suggestions.add(Map.of(
                            "toolName", text(hydrated.get("remoteToolName")),
                            "schemaHash", text(hydrated.get("schemaHash")),
                            "policyId", text(hydrated.get("policyId")),
                            "policyStatus", text(hydrated.get("policyStatus")),
                            "reviewStatus", text(hydrated.get("reviewStatus"))));
                    Map<String, Object> discovered =
                            new LinkedHashMap<>(definition);
                    discovered.put(
                            "schemaHash",
                            text(hydrated.get("schemaHash")));
                    discovered.put(
                            "policyId",
                            text(hydrated.get("policyId")));
                    discovered.put(
                            "policyStatus",
                            text(hydrated.get("policyStatus")));
                    discovered.put(
                            "reviewStatus",
                            text(hydrated.get("reviewStatus")));
                    discoveredViews.add(discovered);
                }
                return List.copyOf(discoveredViews);
            });
            mcp = externalService.recordDiscovery(
                    input.projectId(),
                    storedMcpId,
                    definitions,
                    "DISCOVERED",
                    "");
        } catch (RuntimeException e) {
            definitions = List.of();
            discoveryStatus = "DISCOVERY_FAILED";
            discoveryError = firstText(
                    e.getMessage(),
                    e.getClass().getSimpleName());
            mcp = externalService.recordDiscovery(
                    input.projectId(),
                    storedMcpId,
                    List.of(),
                    "DISCOVERY_FAILED",
                    discoveryError);
        }
        return new Result(
                storedMcpId,
                endpoint.toString(),
                credentialRef,
                discoveryStatus,
                discoveryError,
                mcp,
                definitions,
                suggestions);
    }

    private McpAuthoritativeToolDefinition authoritativeDefinition(
            Map<String, Object> definition) {
        Map<String, Object> safe = definition == null ? Map.of() : definition;
        Object schema = safe.containsKey("inputSchema")
                ? safe.get("inputSchema")
                : safe.get("schema");
        return new McpAuthoritativeToolDefinition(
                text(safe.get("description")),
                schema,
                safe.get("outputSchema"),
                "REMOTE_MCP_TOOL_DEFINITION");
    }

    private ProjectExternalMcpApplicationService requireExternalMcpService() {
        if (externalMcpService == null) {
            throw new IllegalStateException(
                    "PROJECT_EXTERNAL_MCP_SERVICE_NOT_CONFIGURED");
        }
        return externalMcpService;
    }

    private OpsProjectMcpRuntimeConfigService
            requireProjectMcpRuntimeConfigService() {
        if (projectMcpRuntimeConfigService == null) {
            throw new IllegalStateException(
                    "PROJECT_MCP_RUNTIME_CONFIG_SERVICE_NOT_CONFIGURED");
        }
        return projectMcpRuntimeConfigService;
    }

    private String slug(String value) {
        String result = firstText(value, "imported-capability")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(result)
                ? result
                : "imported-capability";
    }

    private String require(String value, String code) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(code);
        }
        return value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record Input(
            String projectId,
            String sourceUrl,
            String capabilityName,
            String credentialRef,
            String transportType) {
    }

    record Result(
            String mcpId,
            String endpoint,
            String credentialRef,
            String discoveryStatus,
            String discoveryError,
            Map<String, Object> mcp,
            List<Map<String, Object>> discoveredTools,
            List<Map<String, Object>> policySuggestions) {
    }
}
