package cn.lgs.orbisops.domain.agentdefinition.model;

/** Typed persistence conflict raised while moving the current published pointer. */
public final class AgentDefinitionPublishConflict extends RuntimeException {

    public enum Reason {
        VERSION_CHANGED,
        CURRENT_POINTER_CHANGED
    }

    private final Reason reason;

    public AgentDefinitionPublishConflict(Reason reason) {
        super(reason == null ? "AGENT_DEFINITION_PUBLISH_CONFLICT" : reason.name());
        if (reason == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_PUBLISH_CONFLICT_REASON_REQUIRED");
        }
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
