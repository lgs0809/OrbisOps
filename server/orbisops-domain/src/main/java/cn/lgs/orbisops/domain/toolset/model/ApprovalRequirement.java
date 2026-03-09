package cn.lgs.orbisops.domain.toolset.model;

public enum ApprovalRequirement {
    NONE,
    USER_CONFIRMATION,
    OPERATOR_APPROVAL,
    DUAL_APPROVAL;

    public static ApprovalRequirement from(ToolSemantics semantics) {
        if (semantics == null) throw new IllegalArgumentException("TOOL_SEMANTICS_REQUIRED");
        return semantics.requiresApproval() ? OPERATOR_APPROVAL : NONE;
    }
}
