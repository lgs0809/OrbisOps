package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Router nodes only project existing Graph state; edge conditions perform deterministic routing. */
final class OpsGraphRouterNodeExecutor {

    OpsGraphNodeExecutionResult execute(OpsGraphNodeExecutionContext context) {
        String inputKey = configText(context.node(), "inputKey");
        Object selected = read(context.state(), inputKey);
        if (selected == null) {
            selected = context.input();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (selected instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) result.put(String.valueOf(key), value);
            });
        }
        String outputKey = StringUtils.hasText(context.node().getOutputKey())
                ? context.node().getOutputKey().trim()
                : "selectedRoutes";
        result.put(outputKey, selected == null ? "" : selected);
        return new OpsGraphNodeExecutionResult(
                selected == null ? "" : String.valueOf(selected),
                result);
    }

    private Object read(OverAllState state, String key) {
        if (state == null || !StringUtils.hasText(key)) return null;
        return state.value(key.trim()).orElse(null);
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null) return "";
        Object value = node.getConfig().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
