package cn.lgs.orbisops.domain.toolset.model;

public enum IdempotencyCapability {
    NONE,
    CLIENT_GENERATED_KEY,
    SERVER_RECEIPT;

    public static IdempotencyCapability from(ToolSemantics semantics) {
        if (semantics == null) throw new IllegalArgumentException("TOOL_SEMANTICS_REQUIRED");
        return semantics.idempotent() ? CLIENT_GENERATED_KEY : NONE;
    }
}
