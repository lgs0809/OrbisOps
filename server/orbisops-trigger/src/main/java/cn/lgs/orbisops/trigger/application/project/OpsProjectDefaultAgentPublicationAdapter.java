package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectDefaultAgentPublicationPort;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import org.springframework.stereotype.Component;

@Component
public class OpsProjectDefaultAgentPublicationAdapter implements ProjectDefaultAgentPublicationPort {

    private final IAgentDefinitionRepository repository;

    public OpsProjectDefaultAgentPublicationAdapter(IAgentDefinitionRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public boolean published(String projectId, String agentId) {
        String project = value(projectId);
        String agent = value(agentId);
        if (project.isBlank() || agent.isBlank()) {
            return false;
        }
        return repository.listCurrentEnabled().stream()
                .anyMatch(snapshot -> project.equals(snapshot.projectId())
                        && agent.equals(snapshot.agentId())
                        && snapshot.enabled()
                        && snapshot.lifecycle() == AgentDefinitionLifecycle.PUBLISHED);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
