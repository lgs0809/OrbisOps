package cn.lgs.orbisops.domain.agentdefinition.rule;

public record WorkflowRuleLimits(
        int maxDepth,
        int maxNodes
) {

    public static final WorkflowRuleLimits DEFAULT = new WorkflowRuleLimits(12, 128);

    public WorkflowRuleLimits {
        if (maxDepth < 1 || maxDepth > 64) {
            throw new IllegalArgumentException("WORKFLOW_RULE_MAX_DEPTH_INVALID");
        }
        if (maxNodes < 1 || maxNodes > 4096) {
            throw new IllegalArgumentException("WORKFLOW_RULE_MAX_NODES_INVALID");
        }
    }
}
