package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.capability.CapabilityDependencyReadiness;
import cn.lgs.orbisops.application.capability.CapabilityReadinessEnvironment;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessSnapshot;
import cn.lgs.orbisops.trigger.ops.change.OpsLandingOperationRecoveryService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsCapabilityReadinessEnvironmentAdapterTest {

    @Test
    void adapterNormalizesDependenciesAndLandingRuntimeFacts() {
        OpsToolResultStore toolResults = mock(OpsToolResultStore.class);
        OpsTrustedProofService trustedProof = mock(OpsTrustedProofService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        ChangePackageReadinessPort changePackage = mock(ChangePackageReadinessPort.class);
        OpsLandingOperationRecoveryService recovery = mock(OpsLandingOperationRecoveryService.class);
        when(toolResults.readiness()).thenReturn(Map.of("status", "up", "rows", 3));
        when(audit.readiness()).thenReturn(Map.of("status", "UP"));
        when(trustedProof.readiness()).thenReturn(Map.of("status", "DOWN", "reason", "missing"));
        when(changePackage.readiness()).thenReturn(
                ChangePackageReadinessSnapshot.up("ChangePackageStore", true));
        when(changePackage.approvedLandingEnabled()).thenReturn(true);
        when(changePackage.operationJournalReady()).thenReturn(true);
        when(recovery.isEnabled()).thenReturn(true);
        OpsCapabilityReadinessEnvironmentAdapter adapter = new OpsCapabilityReadinessEnvironmentAdapter(
                toolResults, trustedProof, audit, changePackage, recovery);

        CapabilityReadinessEnvironment environment = adapter.inspect();

        assertTrue(environment.dependency("toolResultStore").up());
        assertEquals("UP", environment.dependency("toolResultStore").attributes().get("status"));
        assertFalse(environment.dependency("trustedProofStore").up());
        assertEquals("missing", environment.dependency("trustedProofStore").reason());
        assertTrue(environment.approvedLandingEnabled());
        assertTrue(environment.operationJournalReady());
        assertTrue(environment.operationRecoveryReady());
        assertTrue(environment.productionToolRuntimeAvailable());
    }

    @Test
    void adapterConvertsProbeFailureToBoundedDownReason() {
        OpsToolResultStore toolResults = mock(OpsToolResultStore.class);
        OpsTrustedProofService trustedProof = mock(OpsTrustedProofService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        ChangePackageReadinessPort changePackage = mock(ChangePackageReadinessPort.class);
        when(toolResults.readiness()).thenThrow(new IllegalStateException("x".repeat(400)));
        when(audit.readiness()).thenReturn(Map.of("status", "UP"));
        when(trustedProof.readiness()).thenReturn(Map.of("status", "UP"));
        when(changePackage.readiness()).thenReturn(
                ChangePackageReadinessSnapshot.up("ChangePackageStore", true));
        OpsCapabilityReadinessEnvironmentAdapter adapter = new OpsCapabilityReadinessEnvironmentAdapter(
                toolResults, trustedProof, audit, changePackage, null);

        CapabilityDependencyReadiness dependency = adapter.inspect().dependency("toolResultStore");

        assertFalse(dependency.up());
        assertEquals(300, dependency.reason().length());
        assertEquals("DOWN", dependency.attributes().get("status"));
    }
}
