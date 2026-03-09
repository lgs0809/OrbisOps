package cn.lgs.orbisops.domain.agentdefinition.model;

/** Aggregate enforcing the immutable-version lifecycle. */
public final class AgentDefinitionVersionAggregate {

    private AgentDefinitionVersionState state;

    private AgentDefinitionVersionAggregate(AgentDefinitionVersionState state) {
        if (state == null) throw new IllegalArgumentException("AGENT_DEFINITION_STATE_REQUIRED");
        this.state = state;
    }

    public static AgentDefinitionVersionAggregate rehydrate(AgentDefinitionVersionState state) {
        return new AgentDefinitionVersionAggregate(state);
    }

    public AgentDefinitionVersionState validate() {
        require(AgentDefinitionLifecycle.DRAFT, "AGENT_DEFINITION_DRAFT_REQUIRED");
        return transition(AgentDefinitionLifecycle.VALIDATED);
    }

    public AgentDefinitionVersionState publish() {
        require(AgentDefinitionLifecycle.VALIDATED, "AGENT_STATIC_VALIDATION_REQUIRED");
        return transition(AgentDefinitionLifecycle.PUBLISHED);
    }

    public AgentDefinitionVersionState republish() {
        if (state.lifecycle() != AgentDefinitionLifecycle.PUBLISHED) {
            throw new IllegalStateException("AGENT_PUBLISHED_VERSION_REQUIRED");
        }
        return state;
    }

    public AgentDefinitionVersionState disable() {
        if (state.lifecycle() == AgentDefinitionLifecycle.DISABLED) {
            throw new IllegalStateException("AGENT_DEFINITION_ALREADY_DISABLED");
        }
        return transition(AgentDefinitionLifecycle.DISABLED);
    }

    public AgentDefinitionVersionState state() {
        return state;
    }

    private AgentDefinitionVersionState transition(AgentDefinitionLifecycle lifecycle) {
        state = new AgentDefinitionVersionState(
                state.agentId(), state.version(), state.definitionHash(), state.projectId(), lifecycle);
        return state;
    }

    private void require(AgentDefinitionLifecycle expected, String error) {
        if (state.lifecycle() != expected) {
            throw new IllegalStateException(error + ":actual=" + state.lifecycle().name());
        }
    }
}
