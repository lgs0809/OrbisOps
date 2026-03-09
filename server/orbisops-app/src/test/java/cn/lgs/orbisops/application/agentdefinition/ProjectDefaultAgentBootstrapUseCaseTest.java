package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.application.agenteval.AgentEvalCreateSuiteCommand;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectDefaultAgentBootstrapUseCaseTest {

    @Test
    void reusesLatestPublishedDefinitionWithCompleteMicrokernel() {
        Fixture fixture = fixture();
        Definition published = new Definition("published");
        when(fixture.definitions.requireExistingProject("demo-project")).thenReturn("demo-project");
        when(fixture.definitions.versions("demo-project-ops-agent")).thenReturn(List.of(published));
        when(fixture.definitions.facts(published)).thenReturn(new ProjectDefaultAgentDefinitionFacts(
                "demo-project-ops-agent",
                "demo-project",
                4,
                "PUBLISHED",
                "hash-4",
                completeRoles()));

        ProjectDefaultAgentBootstrapResult<Definition> result = fixture.useCase.ensure(
                new ProjectDefaultAgentBootstrapCommand(
                        "demo-project",
                        "示例系统",
                        "operator"));

        assertSame(published, result.definition());
        assertTrue(result.reusedPublishedDefinition());
        assertEquals("reuse-default", result.action());
        verifyNoInteractions(fixture.lifecycle, fixture.eval, fixture.audit);
    }

    @Test
    void createsValidatesEvaluatesAndPublishesInStableOrder() {
        Fixture fixture = fixture();
        Definition template = new Definition("template");
        Definition sanitized = new Definition("sanitized");
        Definition prepared = new Definition("prepared");
        Definition normalized = new Definition("normalized");
        Definition draft = new Definition("draft");
        Definition validated = new Definition("validated");
        Definition released = new Definition("released");
        AgentEvalSuite suite = releaseSuite();
        AgentEvalRunResult evaluation = passedEvaluation();

        when(fixture.definitions.requireExistingProject("demo-project")).thenReturn("demo-project");
        when(fixture.definitions.versions("demo-project-ops-agent")).thenReturn(List.of());
        when(fixture.definitions.loadDefaultTemplate()).thenReturn(template);
        when(fixture.definitions.sanitizeForProject(template, "demo-project")).thenReturn(sanitized);
        when(fixture.definitions.prepareDraft(
                sanitized,
                "demo-project",
                "demo-project-ops-agent",
                "示例系统运维 Agent")).thenReturn(prepared);
        when(fixture.definitions.normalizeExecutionShape(prepared)).thenReturn(normalized);
        when(fixture.lifecycle.saveDraft(normalized)).thenReturn(draft);
        when(fixture.definitions.facts(draft)).thenReturn(facts("DRAFT", 1, ""));
        when(fixture.lifecycle.validate("demo-project-ops-agent", 1)).thenReturn(validated);
        when(fixture.definitions.facts(validated)).thenReturn(facts("VALIDATED", 1, "hash-1"));
        when(fixture.eval.createReleaseSuite(any())).thenReturn(suite);
        when(fixture.eval.runReleaseEvaluation(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "suite-1",
                "operator")).thenReturn(evaluation);
        when(fixture.lifecycle.publish("demo-project-ops-agent", 1)).thenReturn(released);

        ProjectDefaultAgentBootstrapResult<Definition> result = fixture.useCase.ensure(
                new ProjectDefaultAgentBootstrapCommand(
                        "demo-project",
                        "示例系统",
                        "operator"));

        assertSame(released, result.definition());
        assertEquals("create-default", result.action());
        assertEquals("suite-1", result.suiteId());
        assertEquals("run-1", result.evalRunId());

        ArgumentCaptor<AgentEvalCreateSuiteCommand> suiteCommand =
                ArgumentCaptor.forClass(AgentEvalCreateSuiteCommand.class);
        InOrder order = inOrder(fixture.lifecycle, fixture.eval, fixture.audit);
        order.verify(fixture.lifecycle).saveDraft(normalized);
        order.verify(fixture.lifecycle).validate("demo-project-ops-agent", 1);
        order.verify(fixture.eval).createReleaseSuite(suiteCommand.capture());
        order.verify(fixture.eval).runReleaseEvaluation(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "suite-1",
                "operator");
        order.verify(fixture.eval).assertReleaseAllowed(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "hash-1");
        order.verify(fixture.lifecycle).publish("demo-project-ops-agent", 1);
        order.verify(fixture.audit).record(
                "create-default",
                "demo-project-ops-agent",
                null,
                released,
                "suite-1",
                "run-1");

        AgentEvalCreateSuiteCommand captured = suiteCommand.getValue();
        assertEquals("demo-project", captured.projectId());
        assertEquals("demo-project-ops-agent", captured.agentId());
        assertEquals(3, captured.cases().size());
        assertEquals("LIGHTWEIGHT_CHAT", captured.cases().get(0).expectedIntent());
        assertEquals("OPS_REPAIR_REQUEST", captured.cases().get(2).expectedIntent());
        assertEquals(List.of("MAIN_ASSISTANT"), captured.cases().get(2).requiredRoles());
    }

    @Test
    void failedEvaluationStopsBeforeReleaseGatePublishAndAudit() {
        Fixture fixture = fixture();
        Definition template = new Definition("template");
        Definition draft = new Definition("draft");
        Definition validated = new Definition("validated");

        when(fixture.definitions.requireExistingProject("demo-project")).thenReturn("demo-project");
        when(fixture.definitions.versions("demo-project-ops-agent")).thenReturn(List.of());
        when(fixture.definitions.loadDefaultTemplate()).thenReturn(template);
        when(fixture.definitions.sanitizeForProject(template, "demo-project")).thenReturn(template);
        when(fixture.definitions.prepareDraft(
                template,
                "demo-project",
                "demo-project-ops-agent",
                "示例系统运维 Agent")).thenReturn(template);
        when(fixture.definitions.normalizeExecutionShape(template)).thenReturn(template);
        when(fixture.lifecycle.saveDraft(template)).thenReturn(draft);
        when(fixture.definitions.facts(draft)).thenReturn(facts("DRAFT", 1, ""));
        when(fixture.lifecycle.validate("demo-project-ops-agent", 1)).thenReturn(validated);
        when(fixture.definitions.facts(validated)).thenReturn(facts("VALIDATED", 1, "hash-1"));
        when(fixture.eval.createReleaseSuite(any())).thenReturn(releaseSuite());
        when(fixture.eval.runReleaseEvaluation(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "suite-1",
                "operator")).thenReturn(failedEvaluation());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fixture.useCase.ensure(new ProjectDefaultAgentBootstrapCommand(
                        "demo-project",
                        "示例系统",
                        "operator")));

        assertTrue(error.getMessage().contains("AGENT_EVAL_GATE_NOT_PASSED"));
        verify(fixture.eval, never()).assertReleaseAllowed(any(), any(), eq(1), any());
        verify(fixture.lifecycle, never()).publish(any(), eq(1));
        verifyNoInteractions(fixture.audit);
    }

    private Fixture fixture() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle =
                mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        ProjectDefaultAgentDefinitionPort<Definition> definitions =
                mock(ProjectDefaultAgentDefinitionPort.class);
        ProjectDefaultAgentEvalPort eval = mock(ProjectDefaultAgentEvalPort.class);
        @SuppressWarnings("unchecked")
        ProjectDefaultAgentAuditPort<Definition> audit =
                mock(ProjectDefaultAgentAuditPort.class);
        return new Fixture(
                new ProjectDefaultAgentBootstrapUseCase<>(
                        lifecycle,
                        definitions,
                        eval,
                        audit),
                lifecycle,
                definitions,
                eval,
                audit);
    }

    private ProjectDefaultAgentDefinitionFacts facts(
            String lifecycle,
            int version,
            String definitionHash) {
        return new ProjectDefaultAgentDefinitionFacts(
                "demo-project-ops-agent",
                "demo-project",
                version,
                lifecycle,
                definitionHash,
                Set.of());
    }

    private AgentEvalSuite releaseSuite() {
        return new AgentEvalSuite(
                "suite-1",
                "demo-project",
                "demo-project-ops-agent",
                "release gate",
                1,
                List.of(mock(AgentEvalCase.class)),
                "operator");
    }

    private AgentEvalRunResult passedEvaluation() {
        return new AgentEvalRunResult(
                "run-1",
                "suite-1",
                "demo-project",
                "demo-project-ops-agent",
                1,
                "hash-1",
                0,
                "PASSED",
                "PASSED",
                3,
                3,
                0,
                List.of(),
                List.of());
    }

    private AgentEvalRunResult failedEvaluation() {
        return new AgentEvalRunResult(
                "run-1",
                "suite-1",
                "demo-project",
                "demo-project-ops-agent",
                1,
                "hash-1",
                0,
                "PASSED",
                "FAILED",
                3,
                2,
                1,
                List.of(),
                List.of());
    }

    private Set<String> completeRoles() {
        return Set.of("MAIN_ASSISTANT");
    }

    private record Definition(String id) {
    }

    private record Fixture(
            ProjectDefaultAgentBootstrapUseCase<Definition> useCase,
            AgentDefinitionLifecycleUseCase<Definition> lifecycle,
            ProjectDefaultAgentDefinitionPort<Definition> definitions,
            ProjectDefaultAgentEvalPort eval,
            ProjectDefaultAgentAuditPort<Definition> audit) {
    }
}
