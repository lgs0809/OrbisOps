package cn.lgs.orbisops.trigger.ops.runtime;

/** Machine-readable MCP failure; dispatch uncertainty is separate from protocol/tool/contract classification. */
public final class OpsMcpCallFailure extends IllegalStateException
        implements cn.lgs.orbisops.domain.toolexecution.model.ToolDispatchFailureEvidence {
    public enum Kind { PROTOCOL_ERROR, TOOL_ERROR, CONTRACT_INVALID, TRANSPORT_ERROR, AUTHORITY_DENIED }
    private final Kind kind;
    private final boolean dispatched;
    private String rawEnvelope;

    public OpsMcpCallFailure(Kind kind, String reason, boolean dispatched) {
        this(kind, reason, dispatched, null);
    }

    public OpsMcpCallFailure(Kind kind, String reason, boolean dispatched, Throwable cause) {
        super("MCP_" + kind.name() + ":" + reason, cause);
        this.kind = kind;
        this.dispatched = dispatched;
    }

    public Kind kind() { return kind; }
    public boolean dispatched() { return dispatched; }
    public String rawEnvelope() { return rawEnvelope; }
    OpsMcpCallFailure envelope(String raw) { this.rawEnvelope = raw; return this; }
}
