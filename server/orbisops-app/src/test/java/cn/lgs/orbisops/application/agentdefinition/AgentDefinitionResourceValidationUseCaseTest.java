package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentMcpServerDefinition;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentMcpServerDefinitionPolicy;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentToolNamePolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDefinitionResourceValidationUseCaseTest {

    @Test
    void validatesProjectAndEveryResourceOwnerThroughOutboundPorts() {
        RecordingCapabilityPort capabilities = new RecordingCapabilityPort();
        RecordingModelKnowledgePort modelKnowledge = new RecordingModelKnowledgePort();
        AgentDefinitionResourceValidationUseCase useCase =
                new AgentDefinitionResourceValidationUseCase(
                        capabilities,
                        modelKnowledge,
                        new AgentMcpServerDefinitionPolicy(new AgentToolNamePolicy()));
        AgentDefinitionResourceValidationRequest request =
                new AgentDefinitionResourceValidationRequest(
                        "project-1",
                        List.of(
                                owner("Agent", "skill-a", "mcp-a", "model-a", "kb-a"),
                                owner("节点 worker", "skill-b", "mcp-b", "model-b", "kb-b")));

        useCase.validate(request);

        assertEquals(List.of("project-1"), capabilities.projects);
        assertEquals(List.of(
                "Agent:skill-a:project-1",
                "节点 worker:skill-b:project-1"), capabilities.skills);
        assertEquals(List.of(
                "Agent MCP:mcp-a:project-1",
                "节点 worker MCP:mcp-b:project-1"), capabilities.mcps);
        assertEquals(List.of(
                "Agent modelId:model-a",
                "节点 worker modelId:model-b"), modelKnowledge.models);
        assertEquals(List.of(
                "Agent knowledgeBaseId:kb-a:project-1",
                "节点 worker knowledgeBaseId:kb-b:project-1"), modelKnowledge.knowledge);
    }

    @Test
    void inlineMcpDomainPolicyRunsInsideTheApplicationUseCase() {
        AgentDefinitionResourceValidationUseCase useCase =
                new AgentDefinitionResourceValidationUseCase(
                        new RecordingCapabilityPort(),
                        new RecordingModelKnowledgePort(),
                        new AgentMcpServerDefinitionPolicy(new AgentToolNamePolicy()));
        AgentDefinitionResourceValidationRequest.ResourceOwner owner =
                new AgentDefinitionResourceValidationRequest.ResourceOwner(
                        "Agent",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        new AgentMcpServerDefinition(List.of(
                                new AgentMcpServerDefinition.Server(
                                        "local",
                                        "stdio",
                                        "",
                                        "",
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        Map.of()))));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.validate(new AgentDefinitionResourceValidationRequest(
                        "project-1",
                        List.of(owner))));

        assertEquals("Agent MCP local 缺少 command", error.getMessage());
    }

    private AgentDefinitionResourceValidationRequest.ResourceOwner owner(
            String owner,
            String skill,
            String mcp,
            String model,
            String knowledge) {
        return new AgentDefinitionResourceValidationRequest.ResourceOwner(
                owner,
                List.of(skill),
                List.of(mcp),
                model,
                knowledge,
                new AgentMcpServerDefinition(List.of()));
    }

    private static final class RecordingCapabilityPort
            implements AgentCapabilityReferenceValidationPort {
        private final List<String> projects = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private final List<String> mcps = new ArrayList<>();

        @Override
        public void validateProject(String projectId) {
            projects.add(projectId);
        }

        @Override
        public void validateSkills(List<String> values, String owner, String projectId) {
            if (values != null && !values.isEmpty()) {
                skills.add(owner + ":" + values.get(0) + ":" + projectId);
            }
        }

        @Override
        public void validateMcpReferences(List<String> values, String owner, String projectId) {
            if (values != null && !values.isEmpty()) {
                mcps.add(owner + ":" + values.get(0) + ":" + projectId);
            }
        }
    }

    private static final class RecordingModelKnowledgePort
            implements AgentModelKnowledgeReferenceValidationPort {
        private final List<String> models = new ArrayList<>();
        private final List<String> knowledge = new ArrayList<>();

        @Override
        public void validateModel(String modelId, String owner) {
            models.add(owner + ":" + modelId);
        }

        @Override
        public void validateKnowledge(String knowledgeBaseId, String owner, String projectId) {
            knowledge.add(owner + ":" + knowledgeBaseId + ":" + projectId);
        }
    }
}
