package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.NodeExecutionStrategy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsNodeExecutionStrategyRegistry {

    private final Map<String, NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String>> strategies;

    public OpsNodeExecutionStrategyRegistry(
            List<NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String>> candidates) {
        Map<String, NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String>> indexed =
                new LinkedHashMap<>();
        for (NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String> strategy : candidates) {
            for (String nodeType : strategy.supportedNodeTypes()) {
                String key = normalize(nodeType);
                if (indexed.putIfAbsent(key, strategy) != null) {
                    throw new IllegalStateException("RUNTIME_NODE_STRATEGY_DUPLICATE:" + key);
                }
            }
        }
        this.strategies = Map.copyOf(indexed);
    }

    public String execute(OpsRuntimeExecutionNode node, OpsRuntimeNodeExecutionContext context) {
        if (node == null) throw new IllegalArgumentException("RUNTIME_NODE_TYPE_REQUIRED");
        NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String> strategy =
                strategies.get(node.name());
        if (strategy == null) throw new IllegalStateException("RUNTIME_NODE_STRATEGY_NOT_FOUND:" + node.name());
        return strategy.execute(node, context);
    }

    public Map<String, String> describe() {
        Map<String, String> result = new LinkedHashMap<>();
        strategies.forEach((nodeType, strategy) -> result.put(nodeType, strategy.strategyId()));
        return Map.copyOf(result);
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.isBlank()) throw new IllegalArgumentException("RUNTIME_NODE_TYPE_REQUIRED");
        return normalized;
    }
}
