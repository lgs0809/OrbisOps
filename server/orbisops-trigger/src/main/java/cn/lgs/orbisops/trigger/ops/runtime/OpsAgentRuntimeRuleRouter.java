package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Selects execution style while keeping one StateGraph infrastructure adapter.
 * Default Chat/Landing are ReAct; user-selected drag/drop definitions are fixed Workflow graphs.
 */
@Component
public class OpsAgentRuntimeRuleRouter {

    public OpsRuntimeExecutionPlan plan(OpsAgentChatRequest request, OpsAgentDefinition definition) {
        String mode = normalizeMode(request == null ? null : request.getMode());
        String executionStyle = "WORKFLOW".equals(mode) ? "WORKFLOW" : "REACT";
        boolean memoryEnabled = request != null && request.getMemoryEnabled() != null
                ? request.getMemoryEnabled()
                : !"SIMPLE".equals(mode);
        String reason = "WORKFLOW".equals(executionStyle)
                ? "user-selected drag/drop definition -> fixed graph topology"
                : "default/platform agent -> ReAct decision loop";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rule", reason);
        metadata.put("executionStyle", executionStyle);
        metadata.put("definitionShape", inferShape(definition));
        metadata.put("memoryEnabled", memoryEnabled);
        return OpsRuntimeExecutionPlan.builder()
                .mode(mode)
                .engine("STATE_GRAPH")
                .adapterKey(OpsUnifiedAgentEngineAdapter.KEY)
                .memoryEnabled(memoryEnabled)
                .hybrid(false)
                .reason(reason)
                .metadata(metadata)
                .build();
    }

    public Map<String, Object> describeRuleTree(List<String> adapterKeys) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("adapters", adapterKeys);
        data.put("productRuntime", OpsUnifiedAgentEngineAdapter.KEY);
        data.put("rules", List.of(
                "AGENT/default -> REACT over unified StateGraph infrastructure",
                "WORKFLOW/user-selected -> fixed drag/drop graph topology",
                "Landing -> platform REACT definition with LANDING runtime authority",
                "Runtime authority is independent from execution style"
        ));
        return data;
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) return "AGENT";
        String normalized = mode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Set.of("SIMPLE", "MULTI_TURN", "AGENT", "WORKFLOW").contains(normalized)
                ? normalized
                : "AGENT";
    }

    private String inferShape(OpsAgentDefinition definition) {
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            return "SYNTHETIC_REACT_NODE";
        }
        return "DECLARED_GRAPH:" + definition.getNodes().size() + "_NODES";
    }
}
