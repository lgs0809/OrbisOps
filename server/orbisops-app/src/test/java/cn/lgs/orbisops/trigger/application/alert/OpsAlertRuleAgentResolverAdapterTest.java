package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertRuleAgentResolverAdapterTest {

    @Test
    void latestPublishedResolvesWithoutPinnedVersion() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        when(definitions.resolveForProject("agent-1", null, false, "project-1"))
                .thenReturn(definition(4, "agent-v4"));
        OpsAlertRuleAgentResolverAdapter adapter = new OpsAlertRuleAgentResolverAdapter(definitions);

        AlertAgentResolution resolution = adapter.resolve(candidate("LATEST_PUBLISHED", null));

        assertEquals(4, resolution.version());
        assertEquals("agent-v4", resolution.definitionHash());
        verify(definitions).resolveForProject("agent-1", null, false, "project-1");
    }

    @Test
    void pinnedVersionIsForwardedToDefinitionGateway() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        when(definitions.resolveForProject("agent-1", 3, false, "project-1"))
                .thenReturn(definition(3, "agent-v3"));
        OpsAlertRuleAgentResolverAdapter adapter = new OpsAlertRuleAgentResolverAdapter(definitions);

        AlertAgentResolution resolution = adapter.resolve(candidate("PINNED_VERSION", 3));

        assertEquals(3, resolution.version());
        verify(definitions).resolveForProject("agent-1", 3, false, "project-1");
    }

    @Test
    void rejectsIncompleteResolvedDefinition() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        when(definitions.resolveForProject("agent-1", null, false, "project-1"))
                .thenReturn(definition(null, ""));
        OpsAlertRuleAgentResolverAdapter adapter = new OpsAlertRuleAgentResolverAdapter(definitions);

        assertEquals("ALERT_AGENT_VERSION_INCOMPLETE",
                assertThrows(IllegalStateException.class,
                        () -> adapter.resolve(candidate("LATEST_PUBLISHED", null))).getMessage());
    }

    private AlertRuleCandidate candidate(String mode, Integer version) {
        return new AlertRuleCandidate(
                null, "Payment Alert", 1, "ALERTMANAGER", "", "", "", Map.of(),
                "", "", "", "project-1", "agent-1", mode, version, "", "",
                30, "5m", true, false, 5, 120, 20, 300);
    }

    private OpsAgentDefinition definition(Integer version, String hash) {
        return OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .version(version)
                .definitionHash(hash)
                .build();
    }
}
