package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageProductMetricsProjection;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.domain.incident.model.IncidentProductMetricsProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProductMetricsServiceTest {

    @Test
    void snapshotUsesAuthoritativeEventProjectionInsteadOfBoundedIncidentScan() {
        IncidentQueryApplicationService incidents = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changePackages = mock(ChangePackageQueryService.class);
        OpsProjectWorkspaceService projects = mock(OpsProjectWorkspaceService.class);
        when(incidents.productMetrics(anyString())).thenReturn(new IncidentProductMetricsProjection(
                1200,
                9850,
                17,
                44,
                32,
                12000L,
                45000L,
                180000L));
        when(changePackages.productMetrics()).thenReturn(new ChangePackageProductMetricsProjection(100, 60, 50, 40, 5, 3, 2));
        when(projects.snapshot()).thenReturn(Map.of("projects", List.of(
                project(true, true, true, true, true),
                project(true, true, false, true, false),
                project(false, false, false, true, false))));
        OpsProductMetricsService service = new OpsProductMetricsService(incidents, changePackages, projects);

        Map<String, Object> snapshot = service.snapshot();

        @SuppressWarnings("unchecked")
        Map<String, Object> sample = (Map<String, Object>) snapshot.get("sample");
        @SuppressWarnings("unchecked")
        Map<String, Object> northStar = (Map<String, Object>) snapshot.get("northStar");
        @SuppressWarnings("unchecked")
        Map<String, Object> activation = (Map<String, Object>) snapshot.get("activation");
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnosis = (Map<String, Object>) snapshot.get("diagnosis");
        @SuppressWarnings("unchecked")
        Map<String, Object> remediation = (Map<String, Object>) snapshot.get("remediation");
        assertEquals("authoritative-incident-event-projection", sample.get("scope"));
        assertEquals(false, sample.get("boundedScan"));
        assertEquals(1200L, sample.get("incidentCount"));
        assertEquals(17L, northStar.get("weeklyHelpfulResolvedIncidents"));
        assertEquals(3L, activation.get("projectCount"));
        assertEquals(2L, activation.get("evidenceConnectedProjects"));
        assertEquals(1L, activation.get("queryProofVerifiedProjects"));
        assertEquals(1L, activation.get("activatedProjects"));
        assertEquals(1L, activation.get("diagnosisReadyProjects"));
        assertEquals(0.3333d, activation.get("activationRate"));
        assertEquals(false, activation.get("highCardinalityLabels"));
        assertEquals(12000L, diagnosis.get("mttaMs"));
        assertEquals(0.5d, remediation.get("approvalRate"));
        assertEquals(0.8d, remediation.get("landedRate"));
        assertEquals(100L, remediation.get("packageCount"));
        verify(incidents).productMetrics(anyString());
        verify(changePackages).productMetrics();
        verify(projects).snapshot();
    }

    private Map<String, Object> project(
            boolean evidence,
            boolean agent,
            boolean queryProof,
            boolean projectCreated,
            boolean diagnosisReady) {
        return Map.of(
                "onboarding", List.of(
                        step("project", projectCreated),
                        step("evidence", evidence),
                        step("query-proof", queryProof),
                        step("agent", agent)),
                "diagnosisReadiness", Map.of("ready", diagnosisReady));
    }

    private Map<String, Object> step(String key, boolean completed) {
        return Map.of("key", key, "completed", completed, "optional", false);
    }
}
