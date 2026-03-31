package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionDeniedException;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsMcpToolExecutionBinding;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsMcpToolExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OpsToolExecutionService {

    public static class ToolBlockedException extends SecurityException {
        private final Map<String, Object> envelope;

        public ToolBlockedException(String message, Map<String, Object> envelope) {
            super(message);
            this.envelope = envelope == null ? Map.of() : Map.copyOf(envelope);
        }

        public Map<String, Object> envelope() {
            return envelope;
        }
    }

    private final ToolExecutionApplicationService toolExecution;
    private final OpsToolExecutionMapper toolMapper;
    private final McpExecutionApplicationService mcpExecution;
    private final OpsMcpExecutionMapper mcpMapper;

    public OpsToolExecutionService(
            ToolExecutionApplicationService toolExecution,
            OpsToolExecutionMapper toolMapper,
            McpExecutionApplicationService mcpExecution,
            OpsMcpExecutionMapper mcpMapper) {
        if (toolExecution == null) throw new IllegalArgumentException("TOOL_EXECUTION_APPLICATION_REQUIRED");
        if (toolMapper == null) throw new IllegalArgumentException("TOOL_EXECUTION_MAPPER_REQUIRED");
        if (mcpExecution == null) throw new IllegalArgumentException("MCP_EXECUTION_APPLICATION_REQUIRED");
        if (mcpMapper == null) throw new IllegalArgumentException("MCP_EXECUTION_MAPPER_REQUIRED");
        this.toolExecution = toolExecution;
        this.toolMapper = toolMapper;
        this.mcpExecution = mcpExecution;
        this.mcpMapper = mcpMapper;
    }

    public Map<String, Object> execute(Map<String, Object> request, String actor) {
        if (request != null && Boolean.TRUE.equals(request.get("requireReadOnly"))) return executeReadOnly(request, actor);
        return toolMapper.view(toolExecution.execute(
                toolMapper.request(request, actor, false)));
    }

    public Map<String, Object> executeReadOnly(Map<String, Object> request, String actor) {
        Map<String, Object> restricted = new LinkedHashMap<>(request);
        restricted.put("requireReadOnly", true);
        String toolset = value(restricted.get("toolsetId"));
        if (toolset.startsWith("mcp.")) {
            // Compatibility identity only. The downstream MCP boundary checks the reviewed policy again.
            restricted.put(OpsMcpToolExecutionBinding.CONTEXT_KEY,
                    new OpsMcpToolExecutionBinding(toolset.substring(4), value(restricted.get("toolName")), true));
            restricted.put("mcpCompatibilityFacade", true);
        }
        return toolMapper.view(toolExecution.execute(toolMapper.request(restricted, actor, false)));
    }

    public Map<String, Object> executeLanding(Map<String, Object> request, String actor) {
        return toolMapper.view(toolExecution.execute(
                toolMapper.request(request, actor, true)));
    }

    public Map<String, Object> executeMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor) {
        return executeTypedMcp(config, toolInput, actor, false, "", null);
    }

    public Map<String, Object> executeMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            String idempotencyKey) {
        return executeTypedMcp(config, toolInput, actor, false, idempotencyKey, null);
    }

    public Map<String, Object> executeMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            boolean readOnlyHint) {
        return executeTypedMcp(config, toolInput, actor, false, "", readOnlyHint);
    }

    public Map<String, Object> executeMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            String idempotencyKey,
            boolean readOnlyHint) {
        return executeTypedMcp(config, toolInput, actor, false, idempotencyKey, readOnlyHint);
    }

    public Map<String, Object> discoverMcpTools(OpsMcpServerConfig config, String actor) {
        return mcpMapper.view(mcpExecution.discover(
                mcpMapper.request(config, "{}", actor, trustedLandingDisclosure(config))));
    }

    public Map<String, Object> enableMcpTool(
            OpsMcpServerConfig config,
            String toolInput,
            String actor) {
        try {
            return mcpMapper.view(mcpExecution.activate(
                    mcpMapper.request(config, toolInput, actor, trustedLandingDisclosure(config))));
        } catch (McpExecutionDeniedException denied) {
            Map<String, Object> envelope = mcpMapper.view(denied.response());
            throw new ToolBlockedException(denied.getMessage(), envelope);
        }
    }

    public Map<String, Object> executeLandingMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor) {
        return executeTypedMcp(config, toolInput, actor, true, "", null);
    }

    public Map<String, Object> executeLandingMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            String idempotencyKey) {
        return executeTypedMcp(config, toolInput, actor, true, idempotencyKey, null);
    }

    public Map<String, Object> executeLandingMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            boolean readOnlyHint) {
        return executeTypedMcp(config, toolInput, actor, true, "", readOnlyHint);
    }

    public Map<String, Object> executeLandingMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            String idempotencyKey,
            boolean readOnlyHint) {
        return executeTypedMcp(config, toolInput, actor, true, idempotencyKey, readOnlyHint, null);
    }

    /**
     * Executes one Landing MCP call with the frozen operation identity selected by the runtime.
     * The operation id is carried only for the corresponding target-write tool; auxiliary
     * diagnostics (including receipt reads) deliberately leave it blank so they cannot be
     * projected as a second completion of the frozen operation.
     */
    public Map<String, Object> executeLandingMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            String idempotencyKey,
            boolean readOnlyHint,
            String operationId) {
        return executeTypedMcp(config, toolInput, actor, true, idempotencyKey, readOnlyHint, operationId);
    }

    private Map<String, Object> executeTypedMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            boolean trustedLandingRuntime,
            String idempotencyKey,
            Boolean readOnlyHint) {
        return executeTypedMcp(
                config, toolInput, actor, trustedLandingRuntime, idempotencyKey, readOnlyHint, null);
    }

    private Map<String, Object> executeTypedMcp(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            boolean trustedLandingRuntime,
            String idempotencyKey,
            Boolean readOnlyHint,
            String operationId) {
        McpExecutionRequest mcpRequest = mcpMapper.request(
                config,
                toolInput,
                actor,
                trustedLandingRuntime);
        String remoteToolName = mcpRequest.requestedToolName();
        if (remoteToolName.isBlank()) {
            throw new IllegalArgumentException("MCP_EXECUTION_TOOL_NAME_REQUIRED");
        }
        String mcpId = mcpRequest.config().mcpId();
        Map<String, Object> requestContext = new LinkedHashMap<>();
        requestContext.put(
                OpsMcpToolExecutionBinding.CONTEXT_KEY,
                new OpsMcpToolExecutionBinding(mcpId, remoteToolName, readOnlyHint));
        requestContext.put("mcpCompatibilityFacade", true);
        if (config != null && config.getWorkSessionClaim() != null
                && !config.getWorkSessionClaim().isEmpty()) {
            requestContext.put("metadata", new LinkedHashMap<>(config.getWorkSessionClaim()));
        }
        putText(requestContext, "agentId", mcpRequest.config().agentId());
        putText(requestContext, "nodeId", mcpRequest.config().nodeId());
        putText(requestContext, "idempotencyKey", first(
                idempotencyKey,
                header(config, "X-Ops-Execution-Key")));
        if (config.getAuthorityDeadline() != null) {
            requestContext.put("authorityDeadline", config.getAuthorityDeadline());
        }

        ToolExecutionRequest toolRequest = new ToolExecutionRequest(
                mcpRequest.config().projectId(),
                value(actor),
                value(actor),
                "mcp." + mcpId,
                remoteToolName,
                trustedLandingRuntime
                        ? ToolExecutionScope.APPROVED_LANDING
                        : ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                arguments(mcpRequest.input()),
                "",
                mcpRequest.config().runId(),
                requestContext,
                landingContext(mcpRequest, trustedLandingRuntime, operationId));
        try {
            ToolExecutionResponse response = toolExecution.execute(toolRequest);
            Map<String, Object> envelope = toolMapper.view(response);
            if (!response.allowed()) {
                throw new ToolBlockedException(response.decision() + "：MCP Tool 被统一 Tool Policy 阻断", envelope);
            }
            // Reaching this point means the unified ToolExecution boundary authorized the
            // MCP call and the synchronous provider dispatch returned successfully.
            // Expose that fact explicitly so audit/evaluation never infers execution from
            // an absent BLOCKED flag.
            envelope.put("remoteCallExecuted", true);
            return envelope;
        } catch (OpsMcpToolExecutionDispatchHandler.OpsMcpToolExecutionDeniedException denied) {
            throw new ToolBlockedException(denied.getMessage(), denied.providerResult());
        }
    }

    private Map<String, Object> landingContext(
            McpExecutionRequest request,
            boolean trustedLandingRuntime,
            String operationId) {
        if (!trustedLandingRuntime) return Map.of();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("landingApproved", request.config().landingApproved());
        putText(context, "changePackageId", request.config().changePackageId());
        putText(context, "approvedPackageHash", request.config().approvedPackageHash());
        if (request.config().approvedPackageVersion() > 0) {
            context.put("approvedPackageVersion", request.config().approvedPackageVersion());
        }
        putText(context, "operationId", first(operationId, request.config().operationId()));
        context.put("internalCaller", OpsToolsetRouter.LANDING_INTERNAL_CALLER);
        context.put("landingRuntimeToken", OpsToolsetRouter.LANDING_RUNTIME_TOKEN);
        return context;
    }

    private Map<String, Object> arguments(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("arguments");
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw != null) {
            result.put("value", raw);
            return result;
        }
        if (input != null) {
            input.forEach(result::put);
            result.remove("toolName");
            result.remove("remoteToolName");
        }
        return result;
    }

    private boolean trustedLandingDisclosure(OpsMcpServerConfig config) {
        return config != null
                && "LANDING".equalsIgnoreCase(value(config.getToolCallStage()))
                && Boolean.TRUE.equals(config.getLandingApproved())
                && "UNIFIED_AGENT_RUNTIME".equals(value(config.getInternalCaller()))
                && !value(config.getChangePackageId()).isBlank()
                && !value(config.getApprovedPackageHash()).isBlank()
                && config.getApprovedPackageVersion() != null
                && config.getApprovedPackageVersion() > 0;
    }

    private void putText(Map<String, Object> target, String key, String value) {
        String normalized = value(value);
        if (!normalized.isBlank()) target.put(key, normalized);
    }

    private String header(OpsMcpServerConfig config, String name) {
        if (config == null || config.getHeaders() == null) return "";
        return value(config.getHeaders().get(name));
    }

    private String first(Object... values) {
        for (Object candidate : values) {
            String normalized = value(candidate);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
