package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionAggregate;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;

/** Application service for immutable Agent Definition version lifecycle. */
public final class AgentDefinitionLifecycleUseCase<D> {

    private final AgentDefinitionLifecyclePort<D> lifecyclePort;
    private final AgentDefinitionValidationPort<D> validationPort;
    private final AgentDefinitionReleaseGatePort<D> releaseGatePort;

    public AgentDefinitionLifecycleUseCase(AgentDefinitionLifecyclePort<D> lifecyclePort,
                                           AgentDefinitionValidationPort<D> validationPort,
                                           AgentDefinitionReleaseGatePort<D> releaseGatePort) {
        if (lifecyclePort == null) throw new IllegalArgumentException("AGENT_DEFINITION_LIFECYCLE_PORT_REQUIRED");
        if (validationPort == null) throw new IllegalArgumentException("AGENT_DEFINITION_VALIDATION_PORT_REQUIRED");
        if (releaseGatePort == null) throw new IllegalArgumentException("AGENT_DEFINITION_RELEASE_GATE_REQUIRED");
        this.lifecyclePort = lifecyclePort;
        this.validationPort = validationPort;
        this.releaseGatePort = releaseGatePort;
    }

    public D saveDraft(D definition) {
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        return lifecyclePort.saveDraft(definition);
    }

    public D validate(String agentId, int version) {
        AgentDefinitionVersionState current = lifecyclePort.state(agentId, version);
        AgentDefinitionVersionAggregate aggregate = AgentDefinitionVersionAggregate.rehydrate(current);
        AgentDefinitionVersionState target = aggregate.validate();
        D definition = lifecyclePort.definition(agentId, version);
        validationPort.validate(definition);
        return lifecyclePort.transition(current, target);
    }

    public D publish(String agentId, int version) {
        AgentDefinitionVersionState current = lifecyclePort.state(agentId, version);
        AgentDefinitionVersionAggregate aggregate = AgentDefinitionVersionAggregate.rehydrate(current);
        AgentDefinitionVersionState target = aggregate.publish();
        D definition = lifecyclePort.definition(agentId, version);
        releaseGatePort.assertReleaseAllowed(definition);
        return lifecyclePort.transition(current, target);
    }

    public D rollback(String agentId, int version) {
        AgentDefinitionVersionState current = lifecyclePort.state(agentId, version);
        AgentDefinitionVersionAggregate.rehydrate(current).republish();
        D definition = lifecyclePort.definition(agentId, version);
        releaseGatePort.assertReleaseAllowed(definition);
        return lifecyclePort.republish(current);
    }

    public boolean disable(String agentId, int version) {
        AgentDefinitionVersionState current = lifecyclePort.state(agentId, version);
        AgentDefinitionVersionAggregate.rehydrate(current).disable();
        return lifecyclePort.disable(current);
    }
}
