package cn.lgs.orbisops.domain.toolset.model;

/** Business effect only. Invocation protocols and infrastructure types do not belong here. */
public enum ToolEffect {
    READ_ONLY,
    SIDE_EFFECTING;

    public static ToolEffect from(ToolSemantics semantics) {
        if (semantics == null) throw new IllegalArgumentException("TOOL_SEMANTICS_REQUIRED");
        return semantics.readOnly() ? READ_ONLY : SIDE_EFFECTING;
    }
}
