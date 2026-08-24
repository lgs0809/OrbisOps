package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpPolicySuggestion;
import cn.lgs.orbisops.application.mcp.McpPolicySuggestionPort;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class OpsMcpPolicySuggestionAdapter implements McpPolicySuggestionPort {

    private static final String SYSTEM_PROMPT = """
            你是受控运维 Agent 平台的 MCP Tool Policy 初判模型。
            只能输出 JSON，不要解释。你的输出只是待人工审核建议，不能直接生效。
            根据 toolName、description、inputSchema 和项目上下文判断工具效果、风险、阶段权限和参数策略。
            UNKNOWN 或证据不足时必须保守：readOnly=false、riskLevel=HIGH、effectType=UNKNOWN、prepare/land/investigateAllowed=false。
            输出字段：effectType,effectScope,mutability,capability,allowedActions,riskLevel,readOnly,investigateAllowed,prepareAllowed,landAllowed,requiresApprovedPackage,requiresHumanApproval,requiresDryRun,requiresRollbackPlan,argumentPolicy,reason,openQuestions。
            """;

    private final OpsAgentLlmClient llmClient;

    public OpsMcpPolicySuggestionAdapter(ObjectProvider<OpsAgentLlmClient> llmClientProvider) {
        this.llmClient = llmClientProvider == null ? null : llmClientProvider.getIfAvailable();
    }

    @Override
    public Optional<McpPolicySuggestion> suggest(SuggestionRequest request) {
        if (request == null || llmClient == null || !llmClient.available()
                || OpsNodeDeadlineContext.remainingMillis(5000) <= 0) return Optional.empty();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", request.projectId());
            payload.put("mcpId", request.mcpId());
            payload.put("toolId", request.toolId());
            payload.put("toolName", request.toolName());
            payload.put("schemaHash", request.schemaHash());
            payload.put("metadataComplete", request.metadataComplete());
            payload.put("toolSnapshot", request.toolSnapshot());
            // Suggestions never grant authority. A slow optional classifier must not hold
            // catalog discovery open for the full interactive-agent model timeout.
            JSONObject raw = OpsNodeDeadlineContext.withTimeout(5, () -> llmClient.chatJsonObject(
                    "ops-mcp-tool-policy-classifier", SYSTEM_PROMPT, JSON.toJSONString(payload)));
            if (raw == null || raw.isEmpty()) return Optional.empty();
            Map<String, Object> source = new LinkedHashMap<>(raw);
            return Optional.of(new McpPolicySuggestion(
                    text(source.get("effectType"), "UNKNOWN"),
                    text(source.get("effectScope"), "UNKNOWN"),
                    text(source.get("mutability"), "UNKNOWN"),
                    text(source.get("capability"), "UNKNOWN"),
                    strings(source.get("allowedActions")),
                    McpRiskLevel.failClosed(text(source.get("riskLevel"), "HIGH")),
                    bool(source.get("readOnly"), false),
                    bool(source.get("investigateAllowed"), false),
                    bool(source.get("prepareAllowed"), false),
                    bool(source.get("landAllowed"), false),
                    bool(source.get("requiresApprovedPackage"), true),
                    bool(source.get("requiresHumanApproval"), true),
                    bool(source.get("requiresDryRun"), true),
                    bool(source.get("requiresRollbackPlan"), true),
                    map(source.get("argumentPolicy")),
                    text(source.get("reason"), ""),
                    strings(source.get("openQuestions")),
                    source));
        } catch (Exception e) {
            log.warn("MCP Tool Policy LLM 初判失败，降级为规则建议：projectId={} mcpId={} toolName={} reason={}",
                    request.projectId(), request.mcpId(), request.toolName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }

    private List<String> strings(Object value) {
        if (value == null) return List.of();
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else {
            for (String item : String.valueOf(value).split("[,;，\\n]")) add(result, item);
        }
        return result.stream().distinct().toList();
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value, "");
        if (!normalized.isBlank()) target.add(normalized);
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value, "");
        return normalized.isBlank() ? fallback
                : "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
