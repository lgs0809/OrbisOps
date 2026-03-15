package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectWorkspaceReadinessRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWorkspaceProjectionApplicationServiceTest {

    @Test
    void missingDefaultAgentHasHighestReadinessPriority() {
        IProjectWorkspaceReadinessRepository readiness = mock(IProjectWorkspaceReadinessRepository.class);
        when(readiness.available()).thenReturn(false);
        ProjectDefaultAgentPublicationPort agents = mock(ProjectDefaultAgentPublicationPort.class);
        ProjectWorkspaceProjectionApplicationService service = service(readiness, agents, mock(ProjectWorkspaceReadinessFailurePort.class));

        ProjectWorkspaceProjection projection = service.project(request("", List.of(), 0));

        assertFalse(projection.defaultAgentPublished());
        assertFalse(projection.readyForInvestigation());
        assertEquals("DEFAULT_AGENT_NOT_CONFIGURED", projection.readinessReason());
        assertEquals("", projection.recommendedScenarioId());
        assertEquals(6, projection.diagnosticScenarios().size());
        assertTrue(projection.diagnosticScenarios().stream().noneMatch(ProjectWorkspaceProjection.DiagnosticScenario::ready));
    }

    @Test
    void publishedAgentAndPersistedCapabilitiesProduceReadyProjection() {
        IProjectWorkspaceReadinessRepository readiness = mock(IProjectWorkspaceReadinessRepository.class);
        when(readiness.available()).thenReturn(true);
        when(readiness.countReadySourceRepositories("demo-project")).thenReturn(1);
        when(readiness.countEnabledExecutionResources("demo-project")).thenReturn(2);
        ProjectDefaultAgentPublicationPort agents = publishedAgent();
        ProjectWorkspaceProjectionApplicationService service = service(readiness, agents, mock(ProjectWorkspaceReadinessFailurePort.class));

        ProjectWorkspaceProjection projection = service.project(request(
                "demo-project-agent",
                List.of(new ProjectWorkspaceProjectionRequest.EvidenceSource("mysql", "ENABLED")),
                1));

        assertEquals(1, projection.dataResourceCount());
        assertEquals(1, projection.sourceRepositoryCount());
        assertEquals(2, projection.executionResourceCount());
        assertEquals(4, projection.resourceCount());
        assertTrue(projection.defaultAgentPublished());
        assertTrue(projection.readyForInvestigation());
        assertEquals("READY", projection.readinessReason());
        assertEquals("GENERAL_DIAGNOSIS", projection.recommendedScenarioId());
        assertTrue(scenario(projection, "SLOW_SQL_ANALYSIS").ready());
        assertTrue(scenario(projection, "CODE_INVESTIGATION").ready());
        assertTrue(projection.onboarding().stream()
                .filter(step -> "resources".equals(step.key()))
                .allMatch(ProjectWorkspaceProjection.OnboardingStep::completed));
    }

    @Test
    void readinessCatalogFailureIsReportedAndFailsOpenPerCatalog() {
        IProjectWorkspaceReadinessRepository readiness = mock(IProjectWorkspaceReadinessRepository.class);
        when(readiness.available()).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("source unavailable");
        when(readiness.countReadySourceRepositories("demo-project")).thenThrow(failure);
        when(readiness.countEnabledExecutionResources("demo-project")).thenReturn(3);
        ProjectWorkspaceReadinessFailurePort failurePort = mock(ProjectWorkspaceReadinessFailurePort.class);
        ProjectWorkspaceProjectionApplicationService service = service(readiness, publishedAgent(), failurePort);

        ProjectWorkspaceProjection projection = service.project(request(
                "demo-project-agent",
                List.of(new ProjectWorkspaceProjectionRequest.EvidenceSource("prometheus", "ENABLED")),
                0));

        assertEquals(0, projection.sourceRepositoryCount());
        assertEquals(3, projection.executionResourceCount());
        assertEquals(3, projection.resourceCount());
        assertTrue(scenario(projection, "METRIC_ANALYSIS").ready());
        verify(failurePort).readinessQueryFailed(eq("source repository"), eq("demo-project"), eq(failure));
    }

    @Test
    void missingEvidenceTypeKeepsLegacyMysqlDefault() {
        IProjectWorkspaceReadinessRepository readiness = mock(IProjectWorkspaceReadinessRepository.class);
        when(readiness.available()).thenReturn(false);
        ProjectWorkspaceProjectionApplicationService service = service(
                readiness,
                publishedAgent(),
                mock(ProjectWorkspaceReadinessFailurePort.class));

        ProjectWorkspaceProjection projection = service.project(request(
                "demo-project-agent",
                List.of(new ProjectWorkspaceProjectionRequest.EvidenceSource("", "ENABLED")),
                1));

        assertTrue(scenario(projection, "SLOW_SQL_ANALYSIS").ready());
        assertEquals("READY", projection.readinessReason());
    }

    @Test
    void disabledEvidenceDoesNotMakeInvestigationReady() {
        IProjectWorkspaceReadinessRepository readiness = mock(IProjectWorkspaceReadinessRepository.class);
        when(readiness.available()).thenReturn(false);
        ProjectWorkspaceProjectionApplicationService service = service(
                readiness,
                publishedAgent(),
                mock(ProjectWorkspaceReadinessFailurePort.class));

        ProjectWorkspaceProjection projection = service.project(request(
                "demo-project-agent",
                List.of(new ProjectWorkspaceProjectionRequest.EvidenceSource("elasticsearch", "DISABLED")),
                0));

        assertTrue(projection.defaultAgentPublished());
        assertFalse(projection.readyForInvestigation());
        assertEquals("NO_RESOURCE_CONNECTED", projection.readinessReason());
        assertFalse(scenario(projection, "LOG_ANALYSIS").ready());
    }

    private ProjectWorkspaceProjectionApplicationService service(
            IProjectWorkspaceReadinessRepository readiness,
            ProjectDefaultAgentPublicationPort agents,
            ProjectWorkspaceReadinessFailurePort failurePort) {
        return new ProjectWorkspaceProjectionApplicationService(readiness, agents, failurePort);
    }

    private ProjectDefaultAgentPublicationPort publishedAgent() {
        ProjectDefaultAgentPublicationPort port = mock(ProjectDefaultAgentPublicationPort.class);
        when(port.published("demo-project", "demo-project-agent")).thenReturn(true);
        return port;
    }

    private ProjectWorkspaceProjectionRequest request(
            String defaultAgentId,
            List<ProjectWorkspaceProjectionRequest.EvidenceSource> evidenceSources,
            int dataResourceCount) {
        return new ProjectWorkspaceProjectionRequest(
                new ProjectWorkspaceProjectionRequest.Project(
                        "demo-project",
                        "示例系统",
                        "description",
                        "ops",
                        List.of("dev", "prod"),
                        "",
                        defaultAgentId,
                        List.of("diagnosis-skill"),
                        List.of(),
                        true,
                        "2026-07-22T10:00:00",
                        "2026-07-22T10:00:00"),
                List.of(new ProjectWorkspaceProjectionRequest.SkillReference(
                        "diagnosis-skill", "diagnosis-skill", "diagnosis-skill", "PROJECT_AUTHORIZED")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                evidenceSources,
                dataResourceCount,
                0);
    }

    private ProjectWorkspaceProjection.DiagnosticScenario scenario(
            ProjectWorkspaceProjection projection,
            String scenarioId) {
        return projection.diagnosticScenarios().stream()
                .filter(scenario -> scenarioId.equals(scenario.scenarioId()))
                .findFirst()
                .orElseThrow();
    }
}
