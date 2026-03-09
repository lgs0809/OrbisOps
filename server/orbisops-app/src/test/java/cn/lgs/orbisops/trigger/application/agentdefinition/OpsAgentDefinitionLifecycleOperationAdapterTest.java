package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionLifecycleOperationAdapterTest {

    @Test
    void currentSnapshotUsesCompatibilityViewAndMissingAgentReturnsNull() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .name("Agent One")
                .version(2)
                .build();
        when(definitions.list()).thenReturn(List.of(definition));
        OpsAgentDefinitionLifecycleOperationAdapter adapter =
                new OpsAgentDefinitionLifecycleOperationAdapter(
                        definitions,
                        new OpsAgentDefinitionViewMapper(),
                        audit);

        Map<String, Object> snapshot = adapter.currentSnapshot("agent-1");

        assertEquals("agent-1", snapshot.get("agentId"));
        assertEquals("project-1", snapshot.get("projectId"));
        assertNull(adapter.currentSnapshot("missing"));
    }

    @Test
    void transitionAndDisableAuditPreserveCompatibilityShape() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .version(2)
                .build();
        OpsAgentDefinitionLifecycleOperationAdapter adapter =
                new OpsAgentDefinitionLifecycleOperationAdapter(
                        definitions,
                        new OpsAgentDefinitionViewMapper(),
                        audit);
        Map<String, Object> before = Map.of("agentId", "agent-1");

        adapter.recordTransition("publish", "agent-1", 2, definition);
        adapter.recordDisable("agent-1", 2, before, true);

        verify(audit).record(
                "agent-definition",
                "publish",
                "agent-1:2",
                null,
                definition);
        verify(audit).record(
                "agent-definition",
                "disable",
                "agent-1:2",
                before,
                Map.of("disabled", true));
    }
}
