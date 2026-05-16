package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsWorkSessionLeaseHeartbeatTest {

    private ScheduledExecutorService scheduler;

    @AfterEach
    void cleanup() {
        Thread.interrupted();
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Test
    void heartbeatsWhileEngineCanBeSilentAndStopsWithScope() throws Exception {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch periodic = new CountDownLatch(2);
        doAnswer(invocation -> {
            if (calls.incrementAndGet() > 1) periodic.countDown();
            return null;
        }).when(runService).heartbeat(org.mockito.ArgumentMatchers.any());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        OpsWorkSessionLeaseHeartbeat heartbeat = new OpsWorkSessionLeaseHeartbeat(
                runService, Duration.ofMillis(15), scheduler);

        OpsWorkSessionLeaseHeartbeat.Scope scope = heartbeat.start(request());
        assertTrue(periodic.await(1, TimeUnit.SECONDS));
        int beforeClose = calls.get();
        scope.assertHealthy();
        scope.close();
        Thread.sleep(80);

        assertEquals(beforeClose, calls.get());
        assertTrue(beforeClose >= 3, "initial ownership heartbeat plus periodic heartbeats are required");
    }

    @Test
    void leaseLossIsFailClosedAndInterruptsOwnerWait() throws Exception {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        RuntimeException lost = new IllegalStateException("WORK_SESSION_LEASE_LOST");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() >= 2) throw lost;
            return null;
        }).when(runService).heartbeat(org.mockito.ArgumentMatchers.any());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        OpsWorkSessionLeaseHeartbeat heartbeat = new OpsWorkSessionLeaseHeartbeat(
                runService, Duration.ofMillis(10), scheduler);

        try (OpsWorkSessionLeaseHeartbeat.Scope scope = heartbeat.start(request())) {
            for (int i = 0; i < 100 && calls.get() < 2; i++) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            IllegalStateException failure = assertThrows(IllegalStateException.class, scope::assertHealthy);
            assertEquals("WORK_SESSION_LEASE_LOST", failure.getMessage());
        }
    }

    @Test
    void heartbeatKeepsImmutableClaimWhenEngineMutatesRequestMetadata() throws Exception {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch periodic = new CountDownLatch(1);
        doAnswer(invocation -> {
            OpsAgentChatRequest heartbeatRequest = invocation.getArgument(0);
            assertEquals("attempt-heartbeat", heartbeatRequest.getMetadata()
                    .get(OpsWorkSessionClaimMetadata.ATTEMPT_ID));
            if (calls.incrementAndGet() > 1) periodic.countDown();
            return null;
        }).when(runService).heartbeat(org.mockito.ArgumentMatchers.any());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        OpsWorkSessionLeaseHeartbeat heartbeat = new OpsWorkSessionLeaseHeartbeat(
                runService, Duration.ofMillis(10), scheduler);
        OpsAgentChatRequest request = request();

        try (OpsWorkSessionLeaseHeartbeat.Scope scope = heartbeat.start(request)) {
            request.getMetadata().clear();
            assertTrue(periodic.await(1, TimeUnit.SECONDS));
            scope.assertHealthy();
        }

        assertTrue(calls.get() >= 2);
    }

    @Test
    void approvalSuspendStopsHeartbeatBeforeReleasingDurableLease() throws Exception {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            return null;
        }).when(runService).heartbeat(org.mockito.ArgumentMatchers.any());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        OpsWorkSessionLeaseHeartbeat heartbeat = new OpsWorkSessionLeaseHeartbeat(
                runService, Duration.ofMillis(10), scheduler);

        OpsWorkSessionLeaseHeartbeat.Scope scope = heartbeat.start(request());
        scope.suspendForApproval();
        int afterSuspend = calls.get();
        Thread.sleep(60);

        verify(runService).suspendForApproval(org.mockito.ArgumentMatchers.any());
        assertEquals(afterSuspend, calls.get());
        scope.assertHealthy();
        scope.close();
    }

    @Test
    void initialOwnershipFailurePreventsEngineDispatchScope() {
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        doThrow(new IllegalStateException("WORK_SESSION_LEASE_LOST"))
                .when(runService).heartbeat(org.mockito.ArgumentMatchers.any());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        OpsWorkSessionLeaseHeartbeat heartbeat = new OpsWorkSessionLeaseHeartbeat(
                runService, Duration.ofMillis(10), scheduler);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> heartbeat.start(request()));
        assertEquals("WORK_SESSION_LEASE_LOST", failure.getMessage());
    }

    private OpsAgentChatRequest request() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-heartbeat");
        metadata.put(OpsWorkSessionClaimMetadata.LEASE_TOKEN, "lease-heartbeat");
        metadata.put(OpsWorkSessionClaimMetadata.FENCING_TOKEN, 1L);
        metadata.put(OpsWorkSessionClaimMetadata.STATE_VERSION, 0L);
        metadata.put(OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, "manifest-heartbeat");
        return OpsAgentChatRequest.builder()
                .runId("run-heartbeat")
                .projectId("demo-project")
                .query("long model call")
                .metadata(metadata)
                .build();
    }
}
