package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentMcpServerDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Protocol-neutral application request for validating all resources referenced by one Agent definition. */
public record AgentDefinitionResourceValidationRequest(
        String projectId,
        List<ResourceOwner> owners) {

    public AgentDefinitionResourceValidationRequest {
        projectId = projectId == null ? "" : projectId.trim();
        owners = owners == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(owners));
    }

    public record ResourceOwner(
            String owner,
            List<String> skills,
            List<String> mcpIds,
            String modelId,
            String knowledgeBaseId,
            AgentMcpServerDefinition inlineMcpServers) {

        public ResourceOwner {
            owner = owner == null ? "" : owner.trim();
            skills = immutable(skills);
            mcpIds = immutable(mcpIds);
            modelId = modelId == null ? "" : modelId.trim();
            knowledgeBaseId = knowledgeBaseId == null ? "" : knowledgeBaseId.trim();
            inlineMcpServers = inlineMcpServers == null
                    ? new AgentMcpServerDefinition(null)
                    : inlineMcpServers;
        }

        private static <T> List<T> immutable(List<T> values) {
            return values == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
}
