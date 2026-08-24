package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Locale;
import java.util.Set;

/** Model dependency follows node execution semantics; human approval needs no model agent. */
final class OpsRuntimeModelRequirement {
    private OpsRuntimeModelRequirement() { }

    static boolean requiresChatModel(OpsRuntimeResourceContext context) {
        if (context == null || context.getAgentScope() != null) return true;
        if (context.getNode() != null) return nodeRequiresChatModel(context.getNode());
        OpsAgentDefinition definition = context.getDefinition();
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) return true;
        return definition.getNodes().stream().anyMatch(OpsRuntimeModelRequirement::nodeRequiresChatModel);
    }

    private static boolean nodeRequiresChatModel(OpsWorkflowNode node) {
        if (node == null) return true;
        String type = node.getType() == null ? "" : node.getType().trim().toUpperCase(Locale.ROOT);
        // A child resolves its own model requirements after the selected branch runs.
        if (Set.of("START", "END", "HUMAN_APPROVAL", "ROUTER", "SUB_WORKFLOW").contains(type)) return false;
        String mode = node.getMode() != null && !node.getMode().isBlank() ? node.getMode()
                : String.valueOf(node.getConfig() == null ? "" : node.getConfig().getOrDefault("mode", node.getConfig().getOrDefault("agentMode", "")));
        return !("AGENT".equals(type) && "DIRECT".equalsIgnoreCase(mode.trim()));
    }
}
