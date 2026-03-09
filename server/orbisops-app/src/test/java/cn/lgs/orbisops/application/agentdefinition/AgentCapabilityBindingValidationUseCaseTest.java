package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilityBindingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityBindingValidationUseCaseTest {

    @Test
    void validatesDistinctReferencesThroughOneAuthorizationPort() {
        RecordingAuthorizationPort port = new RecordingAuthorizationPort(
                true,
                Set.of("skill-a"),
                Set.of("mcp-a"),
                Set.of("kb-a"),
                Set.of("target-a"));
        AgentCapabilityBindingValidationUseCase useCase =
                new AgentCapabilityBindingValidationUseCase(
                        port,
                        new AgentCapabilityBindingPolicy());

        AgentCapabilityBindingValidationResult result = useCase.validate(snapshot(List.of(
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.SKILL, "skill-a"),
                binding(AgentCapabilityOwnerType.NODE, "worker", AgentCapabilityType.SKILL, "skill-a"),
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.PROJECT_TOOL, "mcp-a"),
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.KNOWLEDGE_BASE, "kb-a"),
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.EXECUTION_TARGET, "target-a"))));

        assertTrue(result.valid());
        assertEquals(Set.of("skill-a"), result.references().skillRefs());
        assertEquals(Set.of("mcp-a"), result.references().projectToolRefs());
        assertEquals(1, port.skillChecks);
    }

    @Test
    void reportsEveryUnauthorizedReferenceAndDisabledTarget() {
        AgentCapabilityBindingValidationUseCase useCase =
                new AgentCapabilityBindingValidationUseCase(
                        new RecordingAuthorizationPort(true, Set.of(), Set.of(), Set.of(), Set.of()),
                        new AgentCapabilityBindingPolicy());

        AgentCapabilityBindingValidationResult result = useCase.validate(snapshot(List.of(
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.SKILL, "skill-x"),
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.PROJECT_TOOL, "mcp-x"),
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.KNOWLEDGE_BASE, "kb-x"),
                binding(AgentCapabilityOwnerType.AGENT, "", AgentCapabilityType.EXECUTION_TARGET, "target-x"))));

        assertFalse(result.valid());
        assertEquals(4, result.errors().size());
        assertTrue(result.errors().get(0).contains("Skill 未授权"));
        assertTrue(result.errors().get(3).contains("执行目标未授权"));
    }

    @Test
    void inlineMcpIsRejectedWithItsDomainOwnerIdentity() {
        AgentCapabilityBindingValidationUseCase useCase =
                new AgentCapabilityBindingValidationUseCase(
                        new RecordingAuthorizationPort(true, Set.of(), Set.of(), Set.of(), Set.of()),
                        new AgentCapabilityBindingPolicy());

        AgentCapabilityBindingValidationResult result = useCase.validate(snapshot(List.of(
                binding(AgentCapabilityOwnerType.NODE, "worker",
                        AgentCapabilityType.INLINE_MCP_SERVER, "local"))));

        assertFalse(result.valid());
        assertEquals(List.of("NODE:worker"), result.references().inlineMcpOwners());
        assertTrue(result.errors().get(0).contains("不允许使用内联 MCP 配置：NODE:worker"));
    }

    @Test
    void missingOrUnknownProjectFailsBeforeCapabilityLookups() {
        AgentCapabilityBindingValidationUseCase useCase =
                new AgentCapabilityBindingValidationUseCase(
                        new RecordingAuthorizationPort(false, Set.of(), Set.of(), Set.of(), Set.of()),
                        new AgentCapabilityBindingPolicy());

        AgentCapabilityBindingValidationResult unknown = useCase.validate(
                new AgentCapabilityBindingSnapshot(
                        "agent-1", 1, AgentDefinitionLifecycle.DRAFT,
                        "missing", List.of()));

        assertEquals(List.of("项目不存在：missing"), unknown.errors());
        assertThrows(IllegalArgumentException.class, unknown::requireValid);
    }

    private AgentCapabilityBindingSnapshot snapshot(List<AgentCapabilityBinding> bindings) {
        return new AgentCapabilityBindingSnapshot(
                "agent-1",
                1,
                AgentDefinitionLifecycle.DRAFT,
                "project-1",
                bindings);
    }

    private AgentCapabilityBinding binding(
            AgentCapabilityOwnerType ownerType,
            String ownerId,
            AgentCapabilityType type,
            String id) {
        return AgentCapabilityBinding.create(
                "agent-1",
                1,
                AgentDefinitionLifecycle.DRAFT,
                "project-1",
                ownerType,
                ownerId,
                type,
                id,
                Map.of());
    }

    private static final class RecordingAuthorizationPort
            implements AgentCapabilityAuthorizationPort {
        private final boolean projectExists;
        private final Set<String> skills;
        private final Set<String> tools;
        private final Set<String> knowledge;
        private final Set<String> targets;
        private int skillChecks;

        private RecordingAuthorizationPort(
                boolean projectExists,
                Set<String> skills,
                Set<String> tools,
                Set<String> knowledge,
                Set<String> targets) {
            this.projectExists = projectExists;
            this.skills = skills;
            this.tools = tools;
            this.knowledge = knowledge;
            this.targets = targets;
        }

        @Override
        public boolean projectExists(String projectId) {
            return projectExists;
        }

        @Override
        public boolean skillAllowed(String projectId, String skillId) {
            skillChecks++;
            return skills.contains(skillId);
        }

        @Override
        public boolean projectToolAllowed(String projectId, String toolId) {
            return tools.contains(toolId);
        }

        @Override
        public boolean knowledgeBaseAllowed(String projectId, String knowledgeBaseId) {
            return knowledge.contains(knowledgeBaseId);
        }

        @Override
        public boolean executionTargetEnabled(String projectId, String executionTargetId) {
            return targets.contains(executionTargetId);
        }
    }
}
