package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessSnapshot;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSafetyReadinessHealthIndicatorTest {

    @Test
    void healthIsUpOnlyWhenAllSafetyStoresAreReady() {
        OpsSafetyReadinessHealthIndicator indicator = new OpsSafetyReadinessHealthIndicator(
                provider(toolResultStore("UP")),
                provider(trustedProofService("UP")),
                provider(auditService("UP")),
                provider(changePackageReadiness("UP")));

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void healthIsDownWhenChangePackageSnapshotIsDown() {
        OpsSafetyReadinessHealthIndicator indicator = new OpsSafetyReadinessHealthIndicator(
                provider(toolResultStore("UP")),
                provider(trustedProofService("UP")),
                provider(auditService("UP")),
                provider(changePackageReadiness("DOWN")));

        assertEquals(Status.DOWN, indicator.health().getStatus());
        assertEquals("DOWN", ((Map<?, ?>) indicator.health().getDetails()
                .get("changePackageStore")).get("status"));
    }

    @Test
    void healthIsDownWhenAnyLegacyReadinessMapReportsDown() {
        assertEquals(Status.DOWN, indicator(
                toolResultStore("DOWN"), trustedProofService("UP"), auditService("UP")).health().getStatus());
        assertEquals(Status.DOWN, indicator(
                toolResultStore("UP"), trustedProofService("DOWN"), auditService("UP")).health().getStatus());
        assertEquals(Status.DOWN, indicator(
                toolResultStore("UP"), trustedProofService("UP"), auditService("DOWN")).health().getStatus());
    }

    @Test
    void healthFailsClosedWhenLegacyReadinessStatusIsMissing() {
        OpsToolResultStore toolResultStore = mock(OpsToolResultStore.class);
        when(toolResultStore.readiness()).thenReturn(Map.of("store", "ToolResultStore"));

        var health = indicator(toolResultStore, trustedProofService("UP"), auditService("UP")).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("readiness status missing", ((Map<?, ?>) health.getDetails()
                .get("toolResultStore")).get("reason"));
    }

    @Test
    void healthFailsClosedWhenLegacyReadinessResultIsMissing() {
        OpsToolResultStore toolResultStore = mock(OpsToolResultStore.class);
        when(toolResultStore.readiness()).thenReturn(null);

        var health = indicator(toolResultStore, trustedProofService("UP"), auditService("UP")).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("readiness result missing", ((Map<?, ?>) health.getDetails()
                .get("toolResultStore")).get("reason"));
    }

    @Test
    void healthFailsClosedWhenChangePackageSnapshotIsMissing() {
        ChangePackageReadinessPort readiness = mock(ChangePackageReadinessPort.class);
        when(readiness.readiness()).thenReturn(null);
        OpsSafetyReadinessHealthIndicator indicator = new OpsSafetyReadinessHealthIndicator(
                provider(toolResultStore("UP")),
                provider(trustedProofService("UP")),
                provider(auditService("UP")),
                provider(readiness));

        var health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("readiness snapshot missing", ((Map<?, ?>) health.getDetails()
                .get("changePackageStore")).get("reason"));
    }

    @Test
    void healthIsDownWhenAnySafetyStoreFails() {
        OpsToolResultStore toolResultStore = mock(OpsToolResultStore.class);
        when(toolResultStore.readiness()).thenThrow(new IllegalStateException());
        OpsSafetyReadinessHealthIndicator indicator = indicator(
                toolResultStore, trustedProofService("UP"), auditService("UP"));

        var health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("IllegalStateException", ((Map<?, ?>) health.getDetails()
                .get("toolResultStore")).get("reason"));
    }

    private OpsSafetyReadinessHealthIndicator indicator(
            OpsToolResultStore toolResultStore,
            OpsTrustedProofService trustedProofService,
            OpsConfigAuditService auditService) {
        return new OpsSafetyReadinessHealthIndicator(
                provider(toolResultStore),
                provider(trustedProofService),
                provider(auditService),
                provider(changePackageReadiness("UP")));
    }

    private OpsToolResultStore toolResultStore(String status) {
        OpsToolResultStore store = mock(OpsToolResultStore.class);
        when(store.readiness()).thenReturn(Map.of("status", status));
        return store;
    }

    private OpsTrustedProofService trustedProofService(String status) {
        OpsTrustedProofService store = mock(OpsTrustedProofService.class);
        when(store.readiness()).thenReturn(Map.of("status", status));
        return store;
    }

    private OpsConfigAuditService auditService(String status) {
        OpsConfigAuditService store = mock(OpsConfigAuditService.class);
        when(store.readiness()).thenReturn(Map.of("status", status));
        return store;
    }

    private ChangePackageReadinessPort changePackageReadiness(String status) {
        ChangePackageReadinessPort port = mock(ChangePackageReadinessPort.class);
        ChangePackageReadinessSnapshot snapshot = "UP".equalsIgnoreCase(status)
                ? ChangePackageReadinessSnapshot.up("ChangePackageStore", true)
                : ChangePackageReadinessSnapshot.down("ChangePackageStore", true, "not ready");
        when(port.readiness()).thenReturn(snapshot);
        return port;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
