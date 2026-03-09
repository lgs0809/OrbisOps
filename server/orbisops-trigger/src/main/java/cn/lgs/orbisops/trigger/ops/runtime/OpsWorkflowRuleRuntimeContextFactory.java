package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluationContext;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Projects runtime state into the allow-listed Rule AST field catalog. */
final class OpsWorkflowRuleRuntimeContextFactory {

    WorkflowRuleEvaluationContext create(OverAllState state) {
        if (state == null) return new WorkflowRuleEvaluationContext(Map.of());
        Map<String, Object> runtime = mutableMap(state.data());
        Map<String, Object> nodeOutput = mutableMap(state.data());
        nodeOutput.put("output", state.value("output", ""));
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                state.value("plan", OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.class)
                        .orElse(null);
        if (plan != null) {
            nodeOutput.put("plan", Map.of(
                    "tasks", plan.getTasks() == null ? java.util.List.of() : plan.getTasks()));
        }
        Map<String, Object> approval = rootMap(state.value("approval").orElse(null));
        approval.putIfAbsent("decision", state.value("review_decision", ""));
        approval.putIfAbsent("selectedRoutes", state.value("selectedReviewRoutes", ""));
        Map<String, Object> error = rootMap(state.value("error").orElse(null));
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("input", rootMap(state.value("input").orElse(null)));
        roots.put("runtime", runtime);
        roots.put("nodeOutput", nodeOutput);
        roots.put("toolResult", rootMap(first(
                state.value("toolResult").orElse(null),
                state.value("tool_result").orElse(null))));
        roots.put("evidence", rootMap(state.value("evidence").orElse(null)));
        roots.put("approval", approval);
        roots.put("error", error);
        return new WorkflowRuleEvaluationContext(roots, previousRoots(state));
    }

    private Map<String, Object> previousRoots(OverAllState state) {
        Object previous = first(
                state.value("previousRuleContext").orElse(null),
                state.value("previous_state").orElse(null));
        if (!(previous instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, Object> roots = new LinkedHashMap<>();
        for (String root : cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleField.allowedRoots()) {
            if (map.containsKey(root)) roots.put(root, map.get(root));
        }
        return roots;
    }

    private Map<String, Object> rootMap(Object value) {
        if (value instanceof Map<?, ?> map) return mutableMap(map);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (value != null) {
            result.put("value", value);
            if (value instanceof Throwable error) {
                result.put("message", summary(error));
            } else if (value instanceof CharSequence) {
                result.put("message", String.valueOf(value));
            }
        }
        return result;
    }

    private Map<String, Object> mutableMap(Map<?, ?> values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (values == null) return result;
        values.forEach((key, value) -> {
            if (key != null) result.put(String.valueOf(key), value);
        });
        return result;
    }

    private Object first(Object... values) {
        if (values == null) return null;
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String summary(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
