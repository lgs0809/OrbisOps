package cn.lgs.orbisops.domain.toolset.model;

/** Stable effect, approval and retry semantics for one executable Tool. */
public record ToolSemantics(
        boolean readOnly,
        boolean writesRepairWorkspace,
        boolean writesTargetResource,
        boolean requiresChangePackage,
        boolean requiresApproval,
        ToolRiskLevel riskLevel,
        boolean idempotent,
        boolean retrySafe
) {

    public ToolSemantics {
        if (riskLevel == null) throw new IllegalArgumentException("TOOL_RISK_LEVEL_REQUIRED");
        int effects = (readOnly ? 1 : 0)
                + (writesRepairWorkspace ? 1 : 0)
                + (writesTargetResource ? 1 : 0);
        if (effects > 1) throw new IllegalArgumentException("TOOL_EFFECT_CONFLICT");
        if (writesTargetResource && (!requiresChangePackage || !requiresApproval)) {
            throw new IllegalArgumentException("TARGET_WRITE_GOVERNANCE_REQUIRED");
        }
        if (retrySafe && !idempotent) {
            throw new IllegalArgumentException("RETRY_SAFE_REQUIRES_IDEMPOTENT");
        }
    }

    public static ToolSemantics readOnlyTool() {
        return new ToolSemantics(true, false, false, false, false,
                ToolRiskLevel.LOW, true, true);
    }

    public static ToolSemantics validation() {
        return new ToolSemantics(false, false, false, false, false,
                ToolRiskLevel.MEDIUM, true, true);
    }

    public static ToolSemantics repairWorkspaceWrite() {
        return new ToolSemantics(false, true, false, false, false,
                ToolRiskLevel.MEDIUM, false, false);
    }

    public static ToolSemantics workflowCommand() {
        return new ToolSemantics(false, false, false, false, false,
                ToolRiskLevel.LOW, false, false);
    }

    public static ToolSemantics changePackageCommand() {
        return new ToolSemantics(false, false, false, false, false,
                ToolRiskLevel.MEDIUM, false, false);
    }

    public static ToolSemantics configurationWrite() {
        return new ToolSemantics(false, false, false, false, false,
                ToolRiskLevel.MEDIUM, false, false);
    }

    public static ToolSemantics targetResourceWrite() {
        return new ToolSemantics(false, false, true, true, true,
                ToolRiskLevel.HIGH, false, false);
    }
}
