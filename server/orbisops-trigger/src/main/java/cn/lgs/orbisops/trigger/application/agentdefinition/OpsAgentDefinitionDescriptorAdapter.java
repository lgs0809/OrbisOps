package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionDescriptorPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

/** Trigger descriptor adapter for the legacy Ops Agent Definition model. */
public final class OpsAgentDefinitionDescriptorAdapter
        implements AgentDefinitionDescriptorPort<OpsAgentDefinition> {

    private final AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper;

    public OpsAgentDefinitionDescriptorAdapter(
            AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper) {
        if (snapshotMapper == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_SNAPSHOT_MAPPER_REQUIRED");
        }
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public AgentDefinitionVersionState describe(OpsAgentDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        }
        Integer version = definition.getVersion();
        return new AgentDefinitionVersionState(
                definition.getAgentId(),
                version == null ? 0 : version,
                definition.getDefinitionHash(),
                definition.getProjectId(),
                AgentDefinitionLifecycle.require(definition.getLifecycle()));
    }

    @Override
    public OpsAgentDefinition snapshot(OpsAgentDefinition definition) {
        return snapshotMapper.copy(definition);
    }
}
