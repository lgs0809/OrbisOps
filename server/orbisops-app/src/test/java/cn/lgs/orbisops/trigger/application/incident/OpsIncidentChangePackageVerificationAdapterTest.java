package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentVerificationResult;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsIncidentChangePackageVerificationAdapterTest {

    @Test
    void legacyNaturalLanguageCriteriaRemainInsufficient() {
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        IncidentSnapshot incident = incident("incident-1");
        when(changes.detail("pkg-1")).thenReturn(Map.of(
                "incidentId", "incident-1",
                "status", "LANDED",
                "landingRunId", "lr-1",
                "verificationCriteria", List.of("确认指标恢复正常")));
        OpsIncidentChangePackageVerificationAdapter verifier =
                new OpsIncidentChangePackageVerificationAdapter(changes);

        IncidentVerificationResult result = verifier.verify(incident, "pkg-1");

        assertEquals(IncidentVerificationResult.Status.INSUFFICIENT, result.status());
        assertEquals("VERIFICATION_CRITERIA_NOT_EXECUTABLE", result.evidence().get("reasonCode"));
    }

    @Test
    void executableCriteriaRequireAuthoritativeLandingProofs() {
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        IncidentSnapshot incident = incident("incident-1");
        when(changes.detail("pkg-1")).thenReturn(Map.of(
                "incidentId", "incident-1",
                "status", "LANDED",
                "landingRunId", "lr-1",
                "verificationCriteria", List.of(Map.of("type", "LANDING_OPERATION_POST_CHECKS"))));
        when(changes.landingOperationRuns("pkg-1", 500)).thenReturn(List.of(Map.of(
                "landingRunId", "lr-1",
                "operationId", "op-1",
                "factStatus", "COMPLETED",
                "status", "SUCCEEDED",
                "resultId", "result-1",
                "outputHash", "hash-1",
                "postCheckResult", Map.of("passed", true))));
        OpsIncidentChangePackageVerificationAdapter verifier =
                new OpsIncidentChangePackageVerificationAdapter(changes);

        IncidentVerificationResult result = verifier.verify(incident, "pkg-1");

        assertEquals(IncidentVerificationResult.Status.PASSED, result.status());
        assertEquals("VERIFICATION_PASSED", result.evidence().get("reasonCode"));
    }

    @Test
    void unknownLandingFactNeverPassesVerification() {
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        IncidentSnapshot incident = incident("incident-1");
        when(changes.detail("pkg-1")).thenReturn(Map.of(
                "incidentId", "incident-1",
                "status", "LANDED",
                "landingRunId", "lr-1",
                "verificationCriteria", List.of(Map.of("type", "LANDING_OPERATION_POST_CHECKS"))));
        when(changes.landingOperationRuns("pkg-1", 500)).thenReturn(List.of(Map.of(
                "landingRunId", "lr-1",
                "operationId", "op-1",
                "factStatus", "UNKNOWN",
                "status", "POST_CHECKING")));
        OpsIncidentChangePackageVerificationAdapter verifier =
                new OpsIncidentChangePackageVerificationAdapter(changes);

        IncidentVerificationResult result = verifier.verify(incident, "pkg-1");

        assertEquals(IncidentVerificationResult.Status.INSUFFICIENT, result.status());
        assertEquals("VERIFICATION_LANDING_FACT_UNKNOWN", result.evidence().get("reasonCode"));
    }

    private IncidentSnapshot incident(String incidentId) {
        IncidentSnapshot incident = mock(IncidentSnapshot.class);
        when(incident.incidentId()).thenReturn(incidentId);
        return incident;
    }
}
