package cn.lgs.orbisops.application.capability;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityReadinessUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T08:30:00Z"),
            ZoneOffset.UTC);

    @Test
    void healthyAnalysisDoesNotImplyApprovedLandingReadiness() {
        CapabilityReadinessUseCase useCase = new CapabilityReadinessUseCase(
                () -> environment(true, false, true, false, false), CLOCK);

        CapabilityReadinessSnapshot snapshot = useCase.snapshot();

        assertTrue(snapshot.analysisReady().ready());
        assertTrue(snapshot.changePackageReady().ready());
        assertFalse(snapshot.approvedLandingReady().ready());
        assertTrue(snapshot.approvedLandingReady().reasonCodes().contains("APPROVED_LANDING_DISABLED"));
        assertTrue(snapshot.approvedLandingReady().reasonCodes().contains("LANDING_OPERATION_RECOVERY_UNAVAILABLE"));
        assertTrue(snapshot.approvedLandingReady().reasonCodes().contains("LANDING_TOOL_RUNTIME_UNAVAILABLE"));
        assertEquals(LocalDateTime.of(2026, 7, 30, 8, 30), snapshot.generatedAt());
    }

    @Test
    void legacySandboxReadinessDoesNotBlockPackageOrLanding() {
        CapabilityReadinessUseCase useCase = new CapabilityReadinessUseCase(
                () -> environment(false, true, true, true, true), CLOCK);

        CapabilityReadinessSnapshot snapshot = useCase.snapshot();

        assertTrue(snapshot.analysisReady().ready());
        assertTrue(snapshot.changePackageReady().ready());
        assertTrue(snapshot.approvedLandingReady().ready());
    }

    @Test
    void completeLandingEnvironmentProducesNoReasons() {
        CapabilityReadinessUseCase useCase = new CapabilityReadinessUseCase(
                () -> environment(true, true, true, true, true), CLOCK);

        CapabilityReadinessSnapshot snapshot = useCase.snapshot();

        assertTrue(snapshot.approvedLandingReady().ready());
        assertEquals(List.of(), snapshot.approvedLandingReady().reasonCodes());
    }

    @Test
    void downDependenciesPropagateByCapabilityTier() {
        List<CapabilityDependencyReadiness> dependencies = List.of(
                dependency("toolResultStore", false, false, false),
                dependency("auditStore", true, false, false),
                dependency("trustedProofStore", false, false, false),
                dependency("changePackageStore", true, false, false),
                dependency("sandbox", true, true, true));
        CapabilityReadinessUseCase useCase = new CapabilityReadinessUseCase(
                () -> new CapabilityReadinessEnvironment(
                        dependencies, true, true, true, true), CLOCK);

        CapabilityReadinessSnapshot snapshot = useCase.snapshot();

        assertEquals(List.of("TOOLRESULTSTORE_UNAVAILABLE"),
                snapshot.analysisReady().reasonCodes());
        assertEquals(List.of("TOOLRESULTSTORE_UNAVAILABLE", "TRUSTEDPROOFSTORE_UNAVAILABLE"),
                snapshot.changePackageReady().reasonCodes());
    }

    private CapabilityReadinessEnvironment environment(
            boolean sandboxUsable,
            boolean approvedLandingEnabled,
            boolean journalReady,
            boolean recoveryReady,
            boolean toolRuntimeReady) {
        return new CapabilityReadinessEnvironment(
                List.of(
                        dependency("toolResultStore", true, false, false),
                        dependency("auditStore", true, false, false),
                        dependency("trustedProofStore", true, false, false),
                        dependency("changePackageStore", true, false, false),
                        dependency("sandbox", true, sandboxUsable, sandboxUsable)),
                approvedLandingEnabled,
                journalReady,
                recoveryReady,
                toolRuntimeReady);
    }

    private CapabilityDependencyReadiness dependency(
            String name,
            boolean up,
            boolean usableForValidation,
            boolean productionLandingEligible) {
        return new CapabilityDependencyReadiness(
                name,
                up,
                null,
                usableForValidation,
                productionLandingEligible,
                Map.of("status", up ? "UP" : "DOWN"));
    }
}
