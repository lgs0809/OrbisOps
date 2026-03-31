package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import cn.lgs.orbisops.domain.mcp.model.McpToolCall;
import cn.lgs.orbisops.domain.mcp.service.McpToolPolicyGovernance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class McpRuntimeOperationsApplicationService implements McpRuntimeOperationsPort {

    private final SelectRuntimeMcpToolsQuery runtimeTools;
    private final McpProjectToolCatalogPort catalog;
    private final IMcpRuntimeCatalogRepository runtimeCatalog;
    private final McpDiscoveryPort discovery;
    private final McpRuntimeAuditPort audit;
    private final McpRuntimeOperationsModelMapper mapper;
    private final Clock clock;
    private final boolean progressiveDisclosureEnabled;

    public McpRuntimeOperationsApplicationService(SelectRuntimeMcpToolsQuery runtimeTools,
                                                  McpProjectToolCatalogPort catalog,
                                                  IMcpRuntimeCatalogRepository runtimeCatalog,
                                                  McpDiscoveryPort discovery,
                                                  McpRuntimeAuditPort audit,
                                                  McpJsonCodec jsonCodec,
                                                  McpRuntimePayloadSanitizerPort payloadSanitizer,
                                                  boolean progressiveDisclosureEnabled) {
        this(runtimeTools, catalog, runtimeCatalog, discovery, audit, jsonCodec, payloadSanitizer,
                progressiveDisclosureEnabled, Clock.systemDefaultZone());
    }

    McpRuntimeOperationsApplicationService(SelectRuntimeMcpToolsQuery runtimeTools,
                                           McpProjectToolCatalogPort catalog,
                                           IMcpRuntimeCatalogRepository runtimeCatalog,
                                           McpDiscoveryPort discovery,
                                           McpRuntimeAuditPort audit,
                                           McpJsonCodec jsonCodec,
                                           McpRuntimePayloadSanitizerPort payloadSanitizer,
                                           boolean progressiveDisclosureEnabled,
                                           Clock clock) {
        if (runtimeTools == null) throw new IllegalArgumentException("MCP_RUNTIME_TOOLS_QUERY_REQUIRED");
        if (catalog == null) throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        if (runtimeCatalog == null) throw new IllegalArgumentException("MCP_RUNTIME_CATALOG_REPOSITORY_REQUIRED");
        if (discovery == null) throw new IllegalArgumentException("MCP_DISCOVERY_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("MCP_RUNTIME_AUDIT_PORT_REQUIRED");
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        if (payloadSanitizer == null) throw new IllegalArgumentException("MCP_RUNTIME_PAYLOAD_SANITIZER_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("MCP_RUNTIME_CLOCK_REQUIRED");
        this.runtimeTools = runtimeTools;
        this.catalog = catalog;
        this.runtimeCatalog = runtimeCatalog;
        this.discovery = discovery;
        this.audit = audit;
        this.mapper = new McpRuntimeOperationsModelMapper(
                jsonCodec, payloadSanitizer, new McpToolPolicyGovernance());
        this.clock = clock;
        this.progressiveDisclosureEnabled = progressiveDisclosureEnabled;
    }

    @Override
    public McpRuntimeActivationResult activateRuntimeTool(McpRuntimeActivationRequest command) {
        McpRuntimeActivationRequest required = required(
                command, "MCP_RUNTIME_ACTIVATION_COMMAND_REQUIRED");
        McpHydratedToolSchema schema = discovery.hydrateSchema(new McpSchemaHydrationRequest(
                required.projectId(),
                required.mcpId(),
                required.toolName(),
                required.agentId(),
                required.userId(),
                required.authoritativeDefinition()));
        McpToolPolicyProjection policy = schema.policy();
        if (policy.status() != cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus.ACTIVE
                || !reviewedForExecution(policy.reviewStatus())) {
            throw new SecurityException("MCP_POLICY_NOT_ACTIVE：未审核工具不能激活");
        }
        boolean trustedLanding = required.trustedLandingRuntime()
                && "LANDING".equalsIgnoreCase(required.stage());
        if (trustedLanding) {
            if (!policy.landAllowed()) {
                throw new SecurityException("MCP_TOOL_LANDING_NOT_ALLOWED：当前 Tool Policy 未授权在 LANDING 阶段使用");
            }
        } else if (!mapper.isPreApprovalExecutable(policy)) {
            throw new SecurityException("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：生产写或未知工具不能在审核前激活");
        }

        String activationId = "mcp-activation-" + UUID.randomUUID();
        Map<String, Object> metadata = Map.of(
                "policyId", policy.policyId(),
                "reason", required.reason());
        runtimeStore().saveActivation(new McpRuntimeActivation(
                activationId,
                required.projectId(),
                required.runId(),
                required.sessionId(),
                required.agentId(),
                required.mcpId(),
                required.toolName(),
                schema.schemaHash(),
                policy.exposureTier().name(),
                "ACTIVE",
                LocalDateTime.now(clock).plusHours(2),
                mapper.encode(metadata),
                null,
                null));
        audit.recordRuntimeEvent(
                required.projectId(),
                required.agentId(),
                required.userId().isBlank() ? required.actor() : required.userId(),
                "tool-disclosure",
                "enable",
                activationId,
                policy.riskLevel().name(),
                "SUCCEEDED",
                Map.of(
                        "runId", required.runId(),
                        "mcpId", required.mcpId(),
                        "toolName", required.toolName(),
                        "schemaHash", schema.schemaHash()));
        return new McpRuntimeActivationResult(
                activationId,
                required.runId(),
                required.mcpId(),
                required.toolName(),
                schema.schemaHash(),
                schema.inputSchema(),
                schema.description(),
                policy.argumentPolicy());
    }

    @Override
    public List<Map<String, Object>> runtimeCatalog(String projectId,
                                                     String toolIdOrMcpId,
                                                     List<String> allowedTools,
                                                     List<String> blockedTools) {
        return runtimeTools.runtimeCatalog(projectId, toolIdOrMcpId, allowedTools, blockedTools);
    }

    @Override
    public List<Map<String, Object>> runtimeCatalog(String projectId,
                                                     String toolIdOrMcpId,
                                                     List<String> allowedTools,
                                                     List<String> blockedTools,
                                                     String executionStage) {
        return runtimeTools.runtimeCatalog(
                projectId, toolIdOrMcpId, allowedTools, blockedTools, executionStage);
    }

    @Override
    public List<Map<String, Object>> runtimeExecutableTools(String projectId,
                                                             String toolIdOrMcpId,
                                                             List<String> allowedTools,
                                                             List<String> blockedTools) {
        return runtimeTools.runtimeExecutableTools(projectId, toolIdOrMcpId, allowedTools, blockedTools);
    }

    @Override
    public List<Map<String, Object>> runtimeExecutableTools(String projectId,
                                                             String toolIdOrMcpId,
                                                             List<String> allowedTools,
                                                             List<String> blockedTools,
                                                             String executionStage) {
        return runtimeTools.runtimeExecutableTools(
                projectId, toolIdOrMcpId, allowedTools, blockedTools, executionStage);
    }

    @Override
    public boolean isRuntimeToolActivated(McpCommands.RuntimeActivationCheck command) {
        McpCommands.RuntimeActivationCheck required = required(command, "MCP_RUNTIME_ACTIVATION_CHECK_REQUIRED");
        return runtimeStore().isActivationActive(required.projectId(), required.runId(),
                required.mcpId(), required.toolName());
    }

    @Override
    public boolean requiresRuntimeActivation(McpHydratedToolSchema schema) {
        return progressiveDisclosureEnabled
                && schema != null
                && schema.policy().exposureTier()
                == cn.lgs.orbisops.domain.mcp.model.McpSchemaExposureTier.EXTENSION;
    }

    @Override
    public void recordMcpCall(McpCommands.RuntimeCall command) {
        McpCommands.RuntimeCall required = required(command, "MCP_RUNTIME_CALL_COMMAND_REQUIRED");
        McpRuntimeToolSnapshot tool = toolSnapshot(required.projectId(), required.toolId());
        McpRuntimeCallFacts callFacts = McpRuntimeCallFacts.from(required.metadata());
        String resolvedMcpId = mapper.text(required.mcpId(), tool.mcpId());
        String resolvedToolName = mapper.text(required.toolName(), tool.toolName());
        String riskLevel = callFacts.riskLevel()
                .orElse(tool.riskLevel())
                .name();
        boolean readOnly = callFacts.readOnly().orElse(tool.readOnly());
        String maskedInput = mapper.sanitize(required.input());
        String maskedOutput = mapper.sanitize(required.output());
        String callId = "mcp-call-" + UUID.randomUUID();
        runtimeStore().saveToolCall(new McpToolCall(
                callId, required.projectId(), required.agentId(), required.nodeId(), required.runId(),
                required.toolId(), resolvedMcpId, resolvedToolName, riskLevel, readOnly, required.status(),
                maskedInput, maskedOutput, required.durationMs(),
                "FAILED".equalsIgnoreCase(required.status()) ? mapper.text(maskedOutput) : "", null));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolId", required.toolId());
        payload.put("mcpId", resolvedMcpId);
        payload.put("toolName", resolvedToolName);
        payload.put("durationMs", required.durationMs());
        payload.put("riskLevel", riskLevel);
        payload.put("readOnly", readOnly);
        payload.putAll(required.metadata());
        audit.recordRuntimeEvent(required.projectId(), required.agentId(), "", "mcp-tool-call",
                "BLOCKED".equalsIgnoreCase(required.status()) ? "blocked" : "call", callId,
                riskLevel, required.status(), payload);
    }

    @Override
    public void recordToolRoutingWarning(McpCommands.RoutingWarning command) {
        McpCommands.RoutingWarning required = required(command, "MCP_ROUTING_WARNING_COMMAND_REQUIRED");
        Map<String, Object> payload = new LinkedHashMap<>(required.payload());
        payload.put("runId", required.runId());
        audit.recordRuntimeEvent(required.projectId(), required.agentId(), "", "tool-router",
                "resource-warn", required.resourceId(), "MEDIUM", required.status(), payload);
    }

    private McpRuntimeToolSnapshot toolSnapshot(String projectId, String toolId) {
        try {
            return catalog.list(projectId).stream()
                    .filter(definition -> toolId.equals(definition.mcpId()))
                    .findFirst()
                    .map(definition -> McpRuntimeToolSnapshot.fromDescriptor(toolId, definition))
                    .orElseGet(() -> McpRuntimeToolSnapshot.fallback(toolId));
        } catch (RuntimeException ignored) {
            return McpRuntimeToolSnapshot.fallback(toolId);
        }
    }

    private boolean reviewedForExecution(cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus status) {
        return status == cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.HUMAN_REVIEWED
                || status == cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.SYSTEM_VERIFIED;
    }

    private IMcpRuntimeCatalogRepository runtimeStore() {
        if (!runtimeCatalog.available()) {
            throw new IllegalStateException("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE");
        }
        return runtimeCatalog;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String requiredText(Object value, String reasonCode) {
        String normalized = mapper.text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }
}
