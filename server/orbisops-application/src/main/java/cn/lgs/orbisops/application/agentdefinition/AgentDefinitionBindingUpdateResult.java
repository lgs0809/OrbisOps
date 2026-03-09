package cn.lgs.orbisops.application.agentdefinition;

/** Typed result of saving one new Agent Definition draft with updated bindings. */
public record AgentDefinitionBindingUpdateResult<D, V>(
        D definition,
        V effectiveBindings) {

    public AgentDefinitionBindingUpdateResult {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_BINDING_RESULT_REQUIRED");
        }
    }
}
