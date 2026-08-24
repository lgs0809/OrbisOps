package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OpsModelCancellationTest {
    private OpsRunControlContext.Binding binding(String run, OpsRunCancellationRegistry registry) {
        return new OpsRunControlContext.Binding(run, "project", Map.of(), registry);
    }

    @Test void registrationBeforeAndAfterCancelIsOnceAndIsolatedFromOtherRuns() {
        var registry = new OpsRunCancellationRegistry();
        var first = new AtomicInteger(); var second = new AtomicInteger();
        try (var a = registry.onCancel("a", first::incrementAndGet);
             var b = registry.onCancel("b", second::incrementAndGet)) {
            registry.markCanceled("a"); registry.markCanceled("a");
            assertEquals(1, first.get()); assertEquals(0, second.get());
            try (var late = registry.onCancel("a", first::incrementAndGet)) { assertEquals(2, first.get()); }
        }
        registry.markCanceled("b"); assertEquals(0, second.get());
    }

    @Test void failedCancellationHandleCannotBlockOtherHandles() {
        var registry = new OpsRunCancellationRegistry(); var count = new AtomicInteger();
        try (var failed = registry.onCancel("a", () -> { throw new IllegalStateException("already closed"); });
             var healthy = registry.onCancel("a", count::incrementAndGet)) {
            registry.markCanceled("a"); assertEquals(1, count.get());
        }
    }

    @Test void runningCallUsesCapturedClaimAndCancellationUnblocksOwnerAndWorker() throws Exception {
        var registry = new OpsRunCancellationRegistry(); var control = binding("a", registry);
        var worker = Executors.newSingleThreadExecutor(); var owner = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1); var ended = new CountDownLatch(1);
        try {
            Future<?> response = owner.submit(() -> OpsRunControlContext.withBinding(control, () -> {
                var error = assertThrows(OpsRunCanceledException.class, () -> new OpsLlmModelCallExecutor(worker)
                        .executeCall(() -> {
                            assertSame(control, OpsRunControlContext.current()); started.countDown();
                            try { new CountDownLatch(1).await(); return "unexpected"; }
                            finally { ended.countDown(); }
                        }, null, 30_000));
                assertNotNull(error); assertFalse(Thread.currentThread().isInterrupted());
                return null;
            }));
            assertTrue(started.await(3, TimeUnit.SECONDS)); registry.markCanceled("a");
            response.get(3, TimeUnit.SECONDS); assertTrue(ended.await(3, TimeUnit.SECONDS));
        } finally { owner.shutdownNow(); worker.shutdownNow(); }
    }

    @Test void queuedCallIsCanceledBeforeItsCallableCanStart() throws Exception {
        var registry = new OpsRunCancellationRegistry();
        var worker = Executors.newSingleThreadExecutor(); var owner = Executors.newSingleThreadExecutor();
        var slot = new CountDownLatch(1); var blockerStarted = new CountDownLatch(1); var called = new AtomicBoolean();
        worker.submit(() -> { blockerStarted.countDown(); slot.await(); return null; });
        try {
            assertTrue(blockerStarted.await(3, TimeUnit.SECONDS));
            Future<?> response = owner.submit(() -> OpsRunControlContext.withBinding(binding("a", registry), () -> {
                assertThrows(OpsRunCanceledException.class, () -> new OpsLlmModelCallExecutor(worker)
                        .executeCall(() -> { called.set(true); return "unexpected"; }, null, 30_000));
                return null;
            }));
            registry.markCanceled("a"); response.get(3, TimeUnit.SECONDS);
            slot.countDown(); worker.submit(() -> { }).get(3, TimeUnit.SECONDS);
            assertFalse(called.get());
        } finally { slot.countDown(); owner.shutdownNow(); worker.shutdownNow(); }
    }

    @Test void arbitraryFutureCancellationDoesNotBecomeUserCancellation() {
        var task = new FutureTask<String>(() -> "ok"); task.cancel(true);
        var registry = new OpsRunCancellationRegistry();
        try (var scope = OpsRunControlContext.open(binding("a", registry))) {
            assertThrows(CancellationException.class, () -> OpsModelCallAwaiter.get(task, 1000));
            assertFalse(registry.cancellationRequested("a"));
        }
    }

    @Test void cancellationCannotLeaveALateInterruptAfterTheWaitingScopeCloses() throws Exception {
        var entered=new CountDownLatch(1);var checkAllowed=new CountDownLatch(1);var closeEntered=new CountDownLatch(1);
        var waiter=new java.util.concurrent.atomic.AtomicReference<Thread>();var firstCheck=new AtomicBoolean();
        var registry=new OpsRunCancellationRegistry() {
            @Override public boolean cancellationRequested(String run) {
                if(Thread.currentThread()==waiter.get() && firstCheck.compareAndSet(false,true)) {
                    entered.countDown();
                    try {assertTrue(checkAllowed.await(3,TimeUnit.SECONDS));}
                    catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                }
                return super.cancellationRequested(run);
            }
            @Override public CancellationRegistration onCancel(String run,Runnable action) {
                var original=super.onCancel(run,action);
                return new CancellationRegistration(){public void close(){closeEntered.countDown();original.close();}};
            }
        };
        var halfway=new CountDownLatch(1);var finishCancel=new CountDownLatch(1);
        var task=new FutureTask<String>(()->"not executed");
        Future<String> controlled=new Future<>() {
            public boolean cancel(boolean interrupt) {
                boolean canceled=task.cancel(interrupt);halfway.countDown();
                try {assertTrue(finishCancel.await(3,TimeUnit.SECONDS));}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return canceled;
            }
            public boolean isCancelled(){return task.isCancelled();}
            public boolean isDone(){return task.isDone();}
            public String get() throws InterruptedException,ExecutionException {return task.get();}
            public String get(long time,TimeUnit unit) throws InterruptedException,ExecutionException,TimeoutException {
                return task.get(time,unit);
            }
        };
        var owner=Executors.newSingleThreadExecutor();var canceler=Executors.newSingleThreadExecutor();
        try {
            var result=owner.submit(()->{waiter.set(Thread.currentThread());return OpsRunControlContext.withBinding(binding("late",registry),()->{
                assertThrows(OpsRunCanceledException.class,()->OpsModelCallAwaiter.get(controlled,3000));
                return Thread.currentThread().isInterrupted();
            });});
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            var cancellation=canceler.submit(()->registry.markCanceled("late"));
            assertTrue(halfway.await(3,TimeUnit.SECONDS));checkAllowed.countDown();
            assertTrue(closeEntered.await(3,TimeUnit.SECONDS));assertFalse(result.isDone());
            finishCancel.countDown();cancellation.get(3,TimeUnit.SECONDS);
            assertFalse(result.get(3,TimeUnit.SECONDS),"A canceled Future wakes its waiter; no late owner interrupt may escape the scope");
        } finally {checkAllowed.countDown();finishCancel.countDown();owner.shutdownNow();canceler.shutdownNow();}
    }

    @Test void anUnrelatedOwnerInterruptIsNotClearedByAnAlreadyCanceledRun() {
        var registry=new OpsRunCancellationRegistry();registry.markCanceled("external-interrupt");
        var task=new FutureTask<String>(()->"unused");
        try(var scope=OpsRunControlContext.open(binding("external-interrupt",registry))) {
            Thread.currentThread().interrupt();
            assertThrows(OpsRunCanceledException.class,()->OpsModelCallAwaiter.get(task,1000));
            assertTrue(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();}
    }

    @Test void streamCancelUnsubscribesItsOriginalProducer() throws Exception {
        var registry = new OpsRunCancellationRegistry(); var disposed = new CountDownLatch(1);
        var completed = new CompletableFuture<Throwable>();
        try (var scope = OpsRunControlContext.open(binding("a", registry))) {
            OpsModelCallAwaiter.cancellable(Flux.never().doOnCancel(disposed::countDown))
                    .subscribe(ignored -> fail(), completed::complete);
        }
        registry.markCanceled("a");
        assertInstanceOf(OpsRunCanceledException.class, completed.get(3, TimeUnit.SECONDS));
        assertTrue(disposed.await(3, TimeUnit.SECONDS));
    }

    @Test void executorBackgroundWorkCannotInheritForegroundCancellationButModelCallCan() throws Exception {
        var registry = new OpsRunCancellationRegistry(); var control = binding("a", registry);
        var pool = new OpsTraceAwareExecutorFactory().create(new OpsExecutorPoolSettings(1, 1, 4, 1, "AbortPolicy", "cancel-unit-"));
        try (var scope = OpsRunControlContext.open(control)) {
            assertNull(pool.submit(OpsRunControlContext::current).get(3, TimeUnit.SECONDS));
            assertSame(control, new OpsLlmModelCallExecutor(pool).executeCall(OpsRunControlContext::current, null, 1000));
            registry.markCanceled("a");
            assertNull(pool.submit(OpsRunControlContext::current).get(3, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
