package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.capability.CapabilityDependencyReadiness;
import cn.lgs.orbisops.application.capability.CapabilityReadinessSnapshot;
import cn.lgs.orbisops.application.capability.CapabilityReadinessState;
import cn.lgs.orbisops.application.capability.CapabilityReadinessUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsCapabilityReadinessServiceTest {

    @Test
    void facadeProjectsTypedSnapshotToCompatibilityMap() {
        CapabilityReadinessUseCase useCase = mock(CapabilityReadinessUseCase.class);
        OpsCapabilityReadinessService service = new OpsCapabilityReadinessService(useCase);
        when(useCase.snapshot()).thenReturn(new CapabilityReadinessSnapshot(
                LocalDateTime.of(2026, 7, 30, 8, 30),
                new CapabilityReadinessState(true, List.of()),
                new CapabilityReadinessState(false, List.of("TRUSTEDPROOFSTORE_UNAVAILABLE")),
                new CapabilityReadinessState(false, List.of("APPROVED_LANDING_DISABLED")),
                List.of(new CapabilityDependencyReadiness(
                        "trustedProofStore",
                        false,
                        "missing",
                        false,
                        false,
                        Map.of("status", "DOWN")))));

        Map<String, Object> snapshot = service.snapshot();

        assertEquals("2026-07-30T08:30", snapshot.get("generatedAt"));
        assertTrue(ready(snapshot, "analysisReady"));
        assertFalse(ready(snapshot, "changePackageReady"));
        assertEquals(List.of("TRUSTEDPROOFSTORE_UNAVAILABLE"),
                reasonCodes(snapshot, "changePackageReady"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> dependencies =
                (Map<String, Map<String, Object>>) snapshot.get("dependencies");
        assertEquals("DOWN", dependencies.get("trustedProofStore").get("status"));
    }

    @SuppressWarnings("unchecked")
    private boolean ready(Map<String, Object> snapshot, String capability) {
        return Boolean.TRUE.equals(((Map<String, Object>) snapshot.get(capability)).get("ready"));
    }

    @SuppressWarnings("unchecked")
    private List<String> reasonCodes(Map<String, Object> snapshot, String capability) {
        return (List<String>) ((Map<String, Object>) snapshot.get(capability)).get("reasonCodes");
    }
}
