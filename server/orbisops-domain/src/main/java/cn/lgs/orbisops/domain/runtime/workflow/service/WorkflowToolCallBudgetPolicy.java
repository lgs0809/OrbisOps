package cn.lgs.orbisops.domain.runtime.workflow.service;

import java.util.Map;

/** A graph-wide limit belongs to the frozen START node, never to model/request metadata. */
public final class WorkflowToolCallBudgetPolicy {
    public int limit(Map<String, Object> definition) {
        Object nodes = definition.get("nodes");
        if (!(nodes instanceof Iterable<?> values)) return 0;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> node) || !"START".equals(node.get("type"))
                    || !java.util.Objects.equals(definition.get("startNodeId"), node.get("nodeId"))) continue;
            return node.get("config") instanceof Map<?, ?> config ? configuredLimit(config) : 0;
        }
        return 0;
    }

    public int configuredLimit(Map<?, ?> config) {
        if (config == null || !config.containsKey("maxRealToolCalls")) return 0;
        Object raw = config.get("maxRealToolCalls");
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < 1 || number.intValue() > 1000) {
            throw new IllegalArgumentException("WORKFLOW_REAL_TOOL_CALL_LIMIT_INVALID");
        }
        return number.intValue();
    }
}
