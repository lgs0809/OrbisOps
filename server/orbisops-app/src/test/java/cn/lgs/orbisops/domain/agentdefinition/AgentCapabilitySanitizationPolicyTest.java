package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityCatalogSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerDecision;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerSelection;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilitySanitizationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilitySanitizationPolicyTest {

    private final AgentCapabilitySanitizationPolicy policy =
            new AgentCapabilitySanitizationPolicy();

    @Test
    void intersectsRequestedCapabilitiesAndPreservesOwnerOrder() {
        AgentCapabilityCatalogSnapshot catalog = new AgentCapabilityCatalogSnapshot(
                List.of("kb-main", "kb-secondary"),
                Set.of("skill-a", "skill-c"),
                Set.of("mcp-a"),
                Set.of("target-b"));
        AgentCapabilityOwnerSelection selection = new AgentCapabilityOwnerSelection(
                "NODE:worker",
                List.of("skill-c", "skill-b", "skill-a"),
                List.of("mcp-x", "mcp-a"),
                List.of("target-a", "target-b"),
                false);

        AgentCapabilityOwnerDecision decision = policy.sanitize(
                List.of(selection), catalog).get(0);

        assertEquals("kb-main", decision.knowledgeBaseId());
        assertEquals(List.of("skill-c", "skill-a"), decision.skillIds());
        assertEquals(List.of("mcp-a"), decision.projectToolIds());
        assertEquals(List.of("target-b"), decision.executionTargetIds());
        assertFalse(decision.forceRagEnabled());
    }

    @Test
    void reactOwnerWithoutAuthorizedProjectToolForcesRagFallback() {
        AgentCapabilityOwnerSelection selection = new AgentCapabilityOwnerSelection(
                "NODE:react",
                List.of(),
                List.of("mcp-denied"),
                List.of(),
                true);

        AgentCapabilityOwnerDecision decision = policy.sanitize(
                List.of(selection),
                new AgentCapabilityCatalogSnapshot(
                        List.of("kb-main"), Set.of(), Set.of(), Set.of()))
                .get(0);

        assertTrue(decision.projectToolIds().isEmpty());
        assertTrue(decision.forceRagEnabled());
    }

    @Test
    void nonReactOwnerDoesNotForceRagWhenNoToolSurvives() {
        AgentCapabilityOwnerSelection selection = new AgentCapabilityOwnerSelection(
                "AGENT",
                List.of(),
                List.of("mcp-denied"),
                List.of(),
                false);

        AgentCapabilityOwnerDecision decision = policy.sanitize(
                List.of(selection), null).get(0);

        assertEquals("", decision.knowledgeBaseId());
        assertFalse(decision.forceRagEnabled());
    }
}
