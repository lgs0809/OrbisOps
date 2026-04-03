package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmModelCallExecutorTest {

    @Test
    void propagatesTraceIntoWorkerAndRestoresCallerContext() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);
            OpsLlmTraceContext.Trace trace = trace("span-worker");

            OpsLlmTraceContext.Trace observed = executor.executeCall(
                    OpsLlmTraceContext::current,
                    trace,
                    1000L);

            assertSame(trace, observed);
            assertNull(OpsLlmTraceContext.current());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void expiredDeadlineCancelsBeforeAwaitAndKeepsLegacyMessage() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> executor.executeCall(() -> "unused", trace("expired"), 0L));

            assertEquals("模型调用开始前节点总时限已耗尽", exception.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void timeoutCancelsAndWrapsTimeoutException() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> executor.executeCall(() -> {
                        Thread.sleep(1000L);
                        return "late";
                    }, trace("timeout"), 20L));

            assertTrue(exception.getMessage().contains("模型调用超过当前执行/节点总时限 20ms"));
            assertInstanceOf(TimeoutException.class, exception.getCause());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void executorQueueWaitDoesNotConsumeActualModelCallTimeout() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.submit(() -> {
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(10L);
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);

            String result = executor.executeCall(() -> {
                Thread.sleep(20L);
                return "ok";
            }, trace("queue-wait"), 50L);

            assertEquals("ok", result);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void runtimeFailureIsUnwrappedWithoutAdditionalWrapper() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);
            IllegalArgumentException failure = new IllegalArgumentException("model failed");

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> executor.executeCall(() -> {
                        throw failure;
                    }, trace("runtime"), 1000L));

            assertSame(failure, thrown);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void checkedFailureKeepsLegacyIllegalStateWrapper() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> executor.executeCall(() -> {
                        throw new IOException("io failed");
                    }, trace("checked"), 1000L));

            assertInstanceOf(IOException.class, thrown.getCause());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void interruptedCallerKeepsInterruptFlagAndLegacyMessage() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmModelCallExecutor executor = new OpsLlmModelCallExecutor(executorService);
            Thread.currentThread().interrupt();

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> executor.executeCall(() -> "unused", trace("interrupted"), 1000L));

            assertEquals("模型调用被中断", thrown.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            executorService.shutdownNow();
        }
    }

    private OpsLlmTraceContext.Trace trace(String source) {
        return new OpsLlmTraceContext.Trace(
                null,
                null,
                "owner",
                "node-1",
                "AGENT",
                "model-agent",
                source);
    }
}
