package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsMcpExecutionMapper {

    public McpExecutionRequest request(
            OpsMcpServerConfig config,
            String toolInput,
            String actor,
            boolean trustedLandingRuntime) {
        if (config == null) throw new IllegalArgumentException("MCP 配置不能为空");
        McpExecutionConfig typed = new McpExecutionConfig(
                text(config.getName()),
                text(config.getDescription()),
                text(config.getProjectId()),
                text(config.getRunId()),
                text(config.getAgentId()),
                text(config.getNodeId()),
                first(config.getMcpId(), config.getName()),
                first(config.getToolId(), first(config.getMcpId(), config.getName())),
                text(config.getOperationId()),
                text(config.getTransport()),
                text(config.getCommand()),
                text(config.getUrl()),
                config.getTimeoutSeconds() == null ? 0 : config.getTimeoutSeconds(),
                config.getArgs(),
                config.getEnv(),
                config.getHeaders(),
                config.getToolCapabilities(),
                config.getAllowedTools(),
                config.getNotificationTools(),
                config.getBlockedTools(),
                trustedLandingRuntime && Boolean.TRUE.equals(config.getLandingApproved()),
                text(config.getChangePackageId()),
                text(config.getApprovedPackageHash()),
                config.getApprovedPackageVersion() == null ? 0 : config.getApprovedPackageVersion(),
                config.getAuthorityDeadline());
        return new McpExecutionRequest(
                typed, text(actor), toolInput, parse(toolInput), trustedLandingRuntime);
    }

    public OpsMcpServerConfig legacy(McpExecutionConfig config, String stage) {
        boolean landing = "LANDING".equalsIgnoreCase(stage);
        return OpsMcpServerConfig.builder()
                .name(config.name())
                .description(config.description())
                .projectId(config.projectId())
                .runId(config.runId())
                .agentId(config.agentId())
                .nodeId(config.nodeId())
                .mcpId(config.mcpId())
                .toolId(config.toolId())
                .operationId(config.operationId())
                .authorityDeadline(config.authorityDeadline())
                .toolCallStage(stage)
                .progressiveManaged(true)
                .landingApproved(landing)
                .changePackageId(config.changePackageId())
                .approvedPackageHash(config.approvedPackageHash())
                .approvedPackageVersion(config.approvedPackageVersion())
                .internalCaller(landing ? OpsToolsetRouter.LANDING_INTERNAL_CALLER : "")
                .landingRuntimeToken(landing ? OpsToolsetRouter.LANDING_RUNTIME_TOKEN : "")
                .transport(config.transport())
                .command(config.command())
                .url(config.url())
                .timeoutSeconds(config.timeoutSeconds())
                .args(config.args())
                .env(config.env())
                .headers(config.headers())
                .toolCapabilities(config.toolCapabilities())
                .allowedTools(config.allowedTools())
                .notificationTools(config.notificationTools())
                .blockedTools(config.blockedTools())
                .build();
    }

    public Map<String, Object> view(McpExecutionResponse response) {
        Map<String, Object> result = new LinkedHashMap<>(response.payload());
        structuredProviderResult(result);
        result.put("resultId", response.recorded().resultId());
        result.put("preview", response.recorded().preview());
        result.put("outputHash", response.recorded().outputHash());
        result.put("truncated", response.recorded().truncated());
        result.put("fullOutputRef", response.recorded().fullOutputRef());
        result.put("inputHash", response.recorded().inputHash());
        result.put("durationMs", response.recorded().durationMs());
        result.put("evidenceId", response.recorded().evidenceId());
        result.put("toolsetId", response.toolsetId());
        result.put("toolName", response.toolName());
        result.put("allowed", response.allowed());
        result.put("decision", response.decision());
        result.put("executionScope", response.executionScope());
        return result;
    }

    private void structuredProviderResult(Map<String, Object> result) {
        if (result == null || result.containsKey("providerResult") || result.containsKey("normalizedContent")) return;
        Object raw = result.get("rawPreview");
        if (!(raw instanceof String text) || text.isBlank()) return;
        try {
            Map<String, Object> provider = providerObject(JSON.parse(text));
            if (!provider.isEmpty()) result.put("providerResult", Map.copyOf(provider));
        } catch (RuntimeException ignored) {
            // Non-JSON provider text remains available through rawPreview.
        }
    }

    private Map<String, Object> providerObject(Object parsed) {
        if (parsed instanceof Map<?, ?> map) return copyMap(map);
        if (parsed instanceof List<?> list && list.size() == 1 && list.get(0) instanceof Map<?, ?> content) {
            Object nestedText = content.get("text");
            if (nestedText instanceof String text && !text.isBlank()) {
                Object nested = JSON.parse(text);
                if (nested instanceof Map<?, ?> map) return copyMap(map);
            }
        }
        return Map.of();
    }

    private Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> provider = new LinkedHashMap<>();
        map.forEach((key, value) -> provider.put(String.valueOf(key), value));
        return provider;
    }

    private Map<String, Object> parse(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        Object parsed = JSON.parse(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String first(Object primary, Object fallback) {
        String value = text(primary);
        return value.isBlank() ? text(fallback) : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
