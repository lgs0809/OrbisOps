package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionDeniedException;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** ACL from the unified Tool execution language into the MCP execution context. */
@Component
public final class OpsMcpToolExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final McpExecutionApplicationService mcpExecution;
    private final OpsMcpExecutionMapper mapper;
    private final OpsProjectMcpRuntimeConfigService runtimeConfigs;

    public OpsMcpToolExecutionDispatchHandler(
            McpExecutionApplicationService mcpExecution,
            OpsMcpExecutionMapper mapper,
            OpsProjectMcpRuntimeConfigService runtimeConfigs) {
        if (mcpExecution == null) throw new IllegalArgumentException("MCP_EXECUTION_APPLICATION_REQUIRED");
        if (mapper == null) throw new IllegalArgumentException("MCP_EXECUTION_MAPPER_REQUIRED");
        if (runtimeConfigs == null) throw new IllegalArgumentException("MCP_RUNTIME_CONFIG_SERVICE_REQUIRED");
        this.mcpExecution = mcpExecution;
        this.mapper = mapper;
        this.runtimeConfigs = runtimeConfigs;
    }

    @Override
    public String handlerId() {
        return "mcp-provider";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return target != null
                && target.providerDescriptor() != null
                && target.providerDescriptor().providerType() == ToolProviderType.MCP;
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        if (target == null) throw new IllegalArgumentException("TOOL_EXECUTION_TARGET_REQUIRED");
        if (request == null) throw new IllegalArgumentException("TOOL_EXECUTION_REQUEST_REQUIRED");
        ToolProviderDescriptor provider = target.providerDescriptor();
        if (provider == null || provider.providerType() != ToolProviderType.MCP) {
            throw new IllegalArgumentException("MCP_TOOL_PROVIDER_REQUIRED");
        }
        String mcpId = mcpId(provider, target);
        OpsMcpServerConfig resolved = runtimeConfigs.resolve(request.projectId(), mcpId)
                .orElseThrow(() -> new SecurityException(
                        "MCP_RUNTIME_CONFIG_NOT_FOUND：projectId=" + request.projectId()
                                + "，mcpId=" + mcpId));
        String remoteToolName = first(provider.remoteToolName(), target.toolName());
        OpsMcpServerConfig executionConfig = executionConfig(
                resolved, target, request, mcpId, remoteToolName);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("toolName", remoteToolName);
        input.put("arguments", remoteArguments(target, request, executionConfig, remoteToolName));
        boolean readOnlyVerification = Boolean.TRUE.equals(request.requestContext().get("requireReadOnly"));
        if (readOnlyVerification) input.put("requireReadOnly", true);
        String rawInput = JSON.toJSONString(input);
        McpExecutionRequest mcpRequest = mapper.request(
                executionConfig,
                rawInput,
                request.actor(),
                request.scope() == ToolExecutionScope.APPROVED_LANDING);
        try {
            // Disclosure may be required for an extended tool, but it never executes a business action.
            if (readOnlyVerification) mcpExecution.activate(mcpRequest);
            McpExecutionResponse response = mcpExecution.execute(mcpRequest);
            Map<String, Object> payload = new LinkedHashMap<>(mapper.view(response));
            preserveProviderResultIdentity(payload);
            payload.put("providerType", ToolProviderType.MCP.name());
            payload.put("providerId", mcpId);
            payload.put("remoteToolName", remoteToolName);
            return Map.copyOf(payload);
        } catch (McpExecutionDeniedException denied) {
            Map<String, Object> payload = new LinkedHashMap<>(mapper.view(denied.response()));
            preserveProviderResultIdentity(payload);
            payload.put("providerType", ToolProviderType.MCP.name());
            payload.put("providerId", mcpId);
            payload.put("remoteToolName", remoteToolName);
            throw new OpsMcpToolExecutionDeniedException(
                    denied.getMessage(), Map.copyOf(payload), denied);
        }
    }

    private Map<String, Object> remoteArguments(
            ToolExecutionTarget target,
            ToolExecutionRequest request,
            OpsMcpServerConfig executionConfig,
            String remoteToolName) {
        Map<String, Object> arguments = new LinkedHashMap<>(
                request.arguments() == null ? Map.of() : request.arguments());
        // Service-control MCP schemas require platform identity fields.  The model is
        // allowed to omit them (and often does for a short natural-language request),
        // so the unified dispatcher supplies the authoritative project and actor before
        // the request crosses the remote MCP boundary.  Other MCP tools keep the exact
        // model-provided argument shape because their schemas may reject extra fields.
        if (isServiceControlTool(remoteToolName)) {
            putAuthorityArgument(target, arguments, "projectId", request.projectId());
            putAuthorityArgument(target, arguments, "actor", request.actor());
        }
        if (request.scope() != ToolExecutionScope.APPROVED_LANDING) {
            return Map.copyOf(arguments);
        }
        putAuthorityArgument(target, arguments, "projectId", request.projectId());
        putAuthorityArgument(target, arguments, "actor", request.actor());
        String executionKey = first(
                request.requestContext().get("idempotencyKey"),
                request.landingContext().get("executionKey"));
        if (arguments.containsKey("executionKey") && !executionKey.equals(String.valueOf(arguments.get("executionKey")))) {
            throw new SecurityException("MCP_RUNTIME_OWNED_ARGUMENT_MISMATCH:executionKey");
        }
        putAuthorityArgument(target, arguments, "executionKey", executionKey);
        Instant deadline = authorityDeadline(
                request.requestContext().get("authorityDeadline"),
                executionConfig == null ? null : executionConfig.getAuthorityDeadline());
        if (deadline != null) putAuthorityArgument(target, arguments, "deadline", deadline.toString());
        return Map.copyOf(arguments);
    }

    private void putAuthorityArgument(ToolExecutionTarget target, Map<String, Object> arguments,
                                      String key, String value) {
        if (value == null || value.isBlank()) return;
        // Every MCP contract can reject unknown properties. Keep execution authority
        // in runtime context unless the tool declares the field or the caller supplied it.
        // Supplied identity fields are still overwritten, never trusted as authority.
        Map<String, Object> schema = JSON.parseObject(target.schema().inputSchemaJson());
        Object properties = schema == null ? null : schema.get("properties");
        if (arguments.containsKey(key)
                || (properties instanceof Map<?, ?> fields && fields.containsKey(key))) {
            arguments.put(key, value);
        }
    }

    private boolean isServiceControlTool(String remoteToolName) {
        return Set.of(
                "get_service_status",
                "restart_service_dry_run",
                "restart_service",
                "get_operation_receipt").contains(remoteToolName);
    }

    private void preserveProviderResultIdentity(Map<String, Object> payload) {
        if (payload == null) return;
        alias(payload, "resultId", "providerResultId");
        alias(payload, "evidenceId", "providerEvidenceId");
        alias(payload, "outputHash", "providerOutputHash");
        alias(payload, "inputHash", "providerInputHash");
        alias(payload, "fullOutputRef", "providerFullOutputRef");
    }

    private void alias(Map<String, Object> payload, String source, String target) {
        Object value = payload.get(source);
        if (value != null) payload.put(target, value);
    }

    private OpsMcpServerConfig executionConfig(
            OpsMcpServerConfig source,
            ToolExecutionTarget target,
            ToolExecutionRequest request,
            String mcpId,
            String remoteToolName) {
        Map<String, Object> landing = request.landingContext();
        boolean approvedLanding = request.scope() == ToolExecutionScope.APPROVED_LANDING;
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name(source.getName())
                .description(source.getDescription())
                .projectId(request.projectId())
                .runId(first(request.runId(), source.getRunId()))
                .agentId(first(request.requestContext().get("agentId"), source.getAgentId()))
                .nodeId(first(request.requestContext().get("nodeId"), source.getNodeId()))
                .mcpId(mcpId)
                .toolId(first(source.getToolId(), target.toolsetId()))
                .operationId(first(landing.get("operationId"), source.getOperationId()))
                .authorityDeadline(authorityDeadline(
                        request.requestContext().get("authorityDeadline"),
                        source.getAuthorityDeadline()))
                .progressiveManaged(true)
                .landingApproved(approvedLanding)
                .changePackageId(first(
                        landing.get("changePackageId"),
                        landing.get("packageId"),
                        source.getChangePackageId()))
                .approvedPackageHash(first(
                        landing.get("approvedPackageHash"),
                        landing.get("packageHash"),
                        source.getApprovedPackageHash()))
                .approvedPackageVersion(integer(
                        first(landing.get("approvedPackageVersion"),
                                landing.get("packageVersion"),
                                source.getApprovedPackageVersion())))
                .transport(source.getTransport())
                .command(source.getCommand())
                .url(source.getUrl())
                .timeoutSeconds(source.getTimeoutSeconds())
                .args(source.getArgs())
                .env(source.getEnv())
                .headers(source.getHeaders())
                .toolCapabilities(source.getToolCapabilities())
                .allowedTools(source.getAllowedTools())
                .notificationTools(source.getNotificationTools())
                .blockedTools(source.getBlockedTools())
                .build();
        // Approved receipt reads retain the same server-owned correlation identity.
        // The actual remote schema decides whether it is also a declared argument.
        if (approvedLanding || !Boolean.TRUE.equals(request.requestContext().get("requireReadOnly"))) {
            putHeader(config, "X-Ops-Execution-Key", first(
                    request.requestContext().get("idempotencyKey"),
                    landing.get("executionKey")));
            putHeader(config, "X-Ops-Fencing-Token", first(
                    request.requestContext().get("fencingToken"),
                    landing.get("fencingToken")));
            putHeader(config, "X-Ops-Deadline", first(
                    request.requestContext().get("deadline"),
                    landing.get("deadline")));
        }
        return config;
    }

    private void putHeader(OpsMcpServerConfig config, String name, String value) {
        if (config == null || value == null || value.isBlank()) return;
        config.getHeaders().put(name, value);
    }

    private String mcpId(ToolProviderDescriptor provider, ToolExecutionTarget target) {
        String explicit = first(provider.mcpServerId(), provider.providerId());
        if (!explicit.isBlank() && !"MCP".equalsIgnoreCase(explicit)) return explicit;
        String toolsetId = target.toolsetId();
        if (toolsetId != null && toolsetId.startsWith("mcp.") && toolsetId.length() > 4) {
            return toolsetId.substring(4);
        }
        throw new IllegalArgumentException(
                "MCP_PROVIDER_ID_REQUIRED：" + target.toolsetId() + "/" + target.toolName());
    }

    private Integer integer(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("MCP_APPROVED_PACKAGE_VERSION_INVALID", error);
        }
    }

    private Instant authorityDeadline(Object value, Instant fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        if (value instanceof Instant instant) return instant;
        try {
            return Instant.parse(String.valueOf(value).trim());
        } catch (Exception error) {
            throw new SecurityException("MCP_AUTHORITY_DEADLINE_INVALID", error);
        }
    }

    private String first(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (!text.isBlank()) return text;
        }
        return "";
    }

    public static final class OpsMcpToolExecutionDeniedException extends SecurityException {
        private final Map<String, Object> providerResult;

        public OpsMcpToolExecutionDeniedException(
                String message,
                Map<String, Object> providerResult,
                Throwable cause) {
            super(message, cause);
            this.providerResult = providerResult == null ? Map.of() : Map.copyOf(providerResult);
        }

        public Map<String, Object> providerResult() {
            return providerResult;
        }
    }
}
