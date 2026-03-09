package cn.lgs.orbisops.trigger.application.agenteval;

import cn.lgs.orbisops.application.agenteval.AgentEvalDefinitionPort;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OpsAgentEvalDefinitionAdapter implements AgentEvalDefinitionPort {

    private final OpsAgentDefinitionQueryGateway gateway;
    private final OpsAgentEvalMapper mapper;

    public OpsAgentEvalDefinitionAdapter(
            OpsAgentDefinitionQueryGateway gateway,
            OpsAgentEvalMapper mapper) {
        this.gateway = gateway;
        this.mapper = mapper;
    }

    @Override
    public AgentEvalDefinitionSnapshot resolve(String agentId, int version) {
        return mapper.definition(gateway.resolve(agentId, version, true));
    }

    @Override
    public Optional<AgentEvalDefinitionSnapshot> publishedBaseline(
            String projectId,
            String agentId,
            int excludedVersion) {
        return gateway.listVersions(agentId).stream()
                .filter(definition -> definition != null
                        && projectId.equals(definition.getProjectId())
                        && definition.getVersion() != null
                        && definition.getVersion() != excludedVersion
                        && "PUBLISHED".equalsIgnoreCase(definition.getLifecycle()))
                .max(java.util.Comparator.comparingInt(OpsAgentDefinition::getVersion))
                .map(mapper::definition);
    }
}
