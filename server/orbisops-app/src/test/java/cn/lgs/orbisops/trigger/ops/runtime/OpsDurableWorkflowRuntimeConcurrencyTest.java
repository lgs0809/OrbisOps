package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.DurableWorkflowRuntimeApplicationService;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class OpsDurableWorkflowRuntimeConcurrencyTest {

    @Test
    void transitionsForSameRunMustBeSerializedBeforeCheckpointPersistence() throws Exception {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        DurableWorkflowRuntimeApplicationService runtime = spy(
                new DurableWorkflowRuntimeApplicationService(Instant::now));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(50L);
                return invocation.callRealMethod();
            } finally {
                active.decrementAndGet();
            }
        }).when(runtime).beforeNode(any(), anyString(), anyInt());

        OpsDurableWorkflowRuntimeCoordinator coordinator =
                new OpsDurableWorkflowRuntimeCoordinator(runs, runtime);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .build();
        coordinator.start(request, plan(), Map.of());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> run(start,
                    () -> coordinator.beforeNode(request, "node-a", 3)));
            Future<?> second = executor.submit(() -> run(start,
                    () -> coordinator.beforeNode(request, "node-b", 3)));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, maxActive.get());
        assertEquals(1, coordinator.state(request).node("node-a").attempt());
        assertEquals(1, coordinator.state(request).node("node-b").attempt());
    }

    private void run(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private BoundWorkflowExecutionPlan plan() {
        List<BoundWorkflowNode> nodes = List.of(
                new BoundWorkflowNode(
                        "node-a", "CHAT", "CHAT", "boundary-node", "config-a", List.of()),
                new BoundWorkflowNode(
                        "node-b", "CHAT", "CHAT", "boundary-node", "config-b", List.of()));
        List<BoundWorkflowResourceSnapshot> resources = List.of(
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.MEMORY_CONTEXT,
                        "bundle-1", 0, "context-hash",
                        "RUNTIME_CONTEXT_BUNDLE", true, true),
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.RUNTIME_POLICY,
                        "policy-1", 1, "policy-hash",
                        "RUNTIME_CONTEXT_BUNDLE", true, true));
        List<String> stages = List.of("ACCESS_VALIDATION", "BOUND_PLAN_ASSEMBLY");
        BoundWorkflowPlanPolicy policy = new BoundWorkflowPlanPolicy();
        String hash = policy.calculatePlanHash(
                1, 1, "definition-hash", "agent-1", "session-1", "run-1", "project-1",
                "bundle-1", "context-hash", "node-a", nodes, List.of(), resources, stages);
        return new BoundWorkflowExecutionPlan(
                1, 1, "definition-hash", "agent-1", "session-1", "run-1", "project-1",
                "bundle-1", "context-hash", "node-a", nodes, List.of(), resources,
                hash, Instant.parse("2026-08-02T00:00:00Z"), stages);
    }
}
