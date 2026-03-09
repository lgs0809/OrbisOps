package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.capability.CapabilityDependencyReadiness;
import cn.lgs.orbisops.application.capability.CapabilityReadinessSnapshot;
import cn.lgs.orbisops.application.capability.CapabilityReadinessState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectProductReadinessApplicationServiceTest {

    @Test
    void projectsPersistedProductionLikeEmergencyStopDrillAsEnvironmentValidated() {
        ProjectEmergencyStopAcceptancePort acceptance = projectId ->
                new ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact(
                        true, "activate=46759, release=46765");
        ProjectProductReadinessProjection projection = new ProjectProductReadinessApplicationService(acceptance)
                .project(workspace(), platform());

        ProjectProductReadinessProjection.Check emergencyStop = projection.remediation().checks().stream()
                .filter(check -> "EMERGENCY_STOP".equals(check.key()))
                .findFirst()
                .orElseThrow();
        assertTrue(emergencyStop.ready());
        assertEquals(ProjectProductReadinessProjection.ReadinessLevel.ENVIRONMENT_VALIDATED,
                emergencyStop.level());
        assertTrue(emergencyStop.detail().contains("46759"));
        assertTrue(emergencyStop.detail().contains("46765"));
    }

    @Test
    void keepsEmergencyStopConfiguredButUnvalidatedWithoutPersistedDrillFact() {
        ProjectEmergencyStopAcceptancePort acceptance = projectId ->
                ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact.notValidated();
        ProjectProductReadinessProjection projection = new ProjectProductReadinessApplicationService(acceptance)
                .project(workspace(), platform());

        ProjectProductReadinessProjection.Check emergencyStop = projection.remediation().checks().stream()
                .filter(check -> "EMERGENCY_STOP".equals(check.key()))
                .findFirst()
                .orElseThrow();
        assertTrue(!emergencyStop.ready());
        assertEquals(ProjectProductReadinessProjection.ReadinessLevel.CONFIGURED, emergencyStop.level());
    }

    private ProjectWorkspaceProjection workspace() {
        ProjectWorkspaceProjectionRequest.Project project = new ProjectWorkspaceProjectionRequest.Project(
                "demo-project", "示例", "", "owner", List.of("dev", "prod"), "", "agent", List.of(),
                List.of(), true, "", "");
        return new ProjectWorkspaceProjection(
                project, List.of(), List.of(), List.of(), List.of(), List.of(),
                1, 0, 1, 2, 1, true, true, "READY", List.of(), "", List.of(
                        new ProjectWorkspaceProjection.OnboardingStep("query-proof", "query", true, false)));
    }

    private CapabilityReadinessSnapshot platform() {
        CapabilityDependencyReadiness toolRuntime = new CapabilityDependencyReadiness(
                "toolRuntimeProfile", true, "", true, true, Map.of());
        CapabilityDependencyReadiness auditStore = new CapabilityDependencyReadiness(
                "auditStore", true, "", true, true, Map.of());
        CapabilityDependencyReadiness resultStore = new CapabilityDependencyReadiness(
                "toolResultStore", true, "", true, true, Map.of());
        return new CapabilityReadinessSnapshot(
                LocalDateTime.now(),
                new CapabilityReadinessState(true, List.of()),
                new CapabilityReadinessState(true, List.of()),
                new CapabilityReadinessState(true, List.of()),
                List.of(toolRuntime, auditStore, resultStore));
    }
}
