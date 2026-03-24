package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionBindingUpdateAdapterTest {

    @Test
    void appliesBindingsValidatesProjectAndReturnsStoredEffectiveBindings() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .build();
        List<Map<String, Object>> requested = List.of(Map.of(
                "capabilityType", "skill",
                "capabilityId", "skill-1"));
        List<Map<String, Object>> stored = List.of(Map.of(
                "agentId", "agent-1",
                "capabilityType", "skill",
                "capabilityId", "skill-1"));
        when(definitions.resolve("agent-1", null, true)).thenReturn(definition);
        when(definitions.listCapabilityBindings("agent-1")).thenReturn(stored);
        OpsAgentDefinitionBindingUpdateAdapter adapter =
                new OpsAgentDefinitionBindingUpdateAdapter(
                        definitions,
                        capabilities,
                        new OpsAgentDefinitionExecutionShapeMapper(),
                        audit);

        assertSame(definition, adapter.resolveDraft("agent-1"));
        assertSame(definition, adapter.applyBindings(definition, requested));
        adapter.assertProjectAndBindingsValid(definition);
        assertEquals(stored, adapter.effectiveBindings(definition));

        verify(capabilities).applyBindings(definition, requested);
        verify(capabilities).requireExistingProject("project-1");
        verify(capabilities).assertValid(definition);
    }

    @Test
    void effectiveBindingsFallBackToDefinitionProjectionWhenRowsAreMissing() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .build();
        List<Map<String, Object>> projected = List.of(Map.of(
                "capabilityType", "knowledge_base",
                "capabilityId", "kb-1"));
        when(definitions.listCapabilityBindings("agent-1")).thenReturn(List.of());
        when(capabilities.extractBindings(definition)).thenReturn(projected);
        OpsAgentDefinitionBindingUpdateAdapter adapter =
                new OpsAgentDefinitionBindingUpdateAdapter(
                        definitions,
                        capabilities,
                        new OpsAgentDefinitionExecutionShapeMapper(),
                        audit);

        assertEquals(projected, adapter.effectiveBindings(definition));
        verify(capabilities).extractBindings(definition);
    }

    @Test
    void missingProjectFailsBeforeAuthorizationValidation() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("")
                .build();
        OpsAgentDefinitionBindingUpdateAdapter adapter =
                new OpsAgentDefinitionBindingUpdateAdapter(
                        definitions,
                        capabilities,
                        new OpsAgentDefinitionExecutionShapeMapper(),
                        audit);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.assertProjectAndBindingsValid(definition));

        assertEquals("Agent 必须归属一个项目；跨项目复用请使用复制功能", error.getMessage());
    }

    @Test
    void auditUsesEffectiveBindingsReturnedAfterSave() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition saved = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .build();
        List<Map<String, Object>> effective = List.of(Map.of(
                "capabilityType", "skill",
                "capabilityId", "skill-1"));
        OpsAgentDefinitionBindingUpdateAdapter adapter =
                new OpsAgentDefinitionBindingUpdateAdapter(
                        definitions,
                        capabilities,
                        new OpsAgentDefinitionExecutionShapeMapper(),
                        audit);

        adapter.recordBindingUpdate("agent-1", saved, effective);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(
                org.mockito.ArgumentMatchers.eq("agent-definition"),
                org.mockito.ArgumentMatchers.eq("update-bindings"),
                org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.isNull(),
                captor.capture());
        assertEquals("agent-1", captor.getValue().get("agentId"));
        assertEquals("project-1", captor.getValue().get("projectId"));
        assertEquals(effective, captor.getValue().get("bindings"));
    }
}
