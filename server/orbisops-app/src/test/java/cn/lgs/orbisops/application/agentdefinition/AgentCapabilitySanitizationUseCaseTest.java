package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityCatalogSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerDecision;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerSelection;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilitySanitizationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilitySanitizationUseCaseTest {

    @Test
    void resolvesOneCatalogForAllDistinctRequestedReferences() {
        RecordingCatalogPort port = new RecordingCatalogPort();
        AgentCapabilitySanitizationUseCase useCase =
                new AgentCapabilitySanitizationUseCase(
                        port,
                        new AgentCapabilitySanitizationPolicy());
        AgentCapabilitySanitizationRequest request =
                new AgentCapabilitySanitizationRequest(
                        "project-1",
                        List.of(
                                new AgentCapabilityOwnerSelection(
                                        "AGENT",
                                        List.of("skill-a", "skill-a"),
                                        List.of("mcp-a"),
                                        List.of("target-a"),
                                        false),
                                new AgentCapabilityOwnerSelection(
                                        "NODE:worker",
                                        List.of("skill-b", "skill-a"),
                                        List.of("mcp-b"),
                                        List.of("target-b", "target-a"),
                                        true)));

        List<AgentCapabilityOwnerDecision> decisions = useCase.sanitize(request);

        assertEquals(1, port.calls);
        assertEquals("project-1", port.projectId);
        assertEquals(Set.of("skill-a", "skill-b"), port.skills);
        assertEquals(Set.of("mcp-a", "mcp-b"), port.tools);
        assertEquals(Set.of("target-a", "target-b"), port.targets);
        assertEquals(2, decisions.size());
        assertEquals("kb-main", decisions.get(0).knowledgeBaseId());
        assertTrue(decisions.get(1).forceRagEnabled());
    }

    private static final class RecordingCatalogPort
            implements AgentCapabilityCatalogPort {
        private int calls;
        private String projectId;
        private Set<String> skills;
        private Set<String> tools;
        private Set<String> targets;

        @Override
        public AgentCapabilityCatalogSnapshot resolve(
                String projectId,
                Set<String> requestedSkillIds,
                Set<String> requestedProjectToolIds,
                Set<String> requestedExecutionTargetIds) {
            calls++;
            this.projectId = projectId;
            this.skills = requestedSkillIds;
            this.tools = requestedProjectToolIds;
            this.targets = requestedExecutionTargetIds;
            return new AgentCapabilityCatalogSnapshot(
                    List.of("kb-main"),
                    Set.of("skill-a", "skill-b"),
                    Set.of("mcp-a"),
                    Set.of("target-a", "target-b"));
        }
    }
}
