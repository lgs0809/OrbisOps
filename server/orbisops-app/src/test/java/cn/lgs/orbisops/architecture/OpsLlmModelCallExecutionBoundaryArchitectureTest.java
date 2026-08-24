package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmModelCallExecutionBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesChatExecutionDeadlineTimeoutAndCancellation() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String contentCall = read(OPS + "OpsLlmContentCallService.java");
        String executor = read(OPS + "OpsLlmModelCallExecutor.java");
        String awaiter = read(OPS + "OpsModelCallAwaiter.java");
        String control = read(OPS + "OpsRunControlContext.java");

        assertAll(
                () -> assertTrue(client.contains("OpsLlmContentCallService contentCallService")),
                () -> assertTrue(client.contains("contentCallService.call(")),
                () -> assertFalse(client.contains("modelCallExecutor.execute(")),
                () -> assertFalse(client.contains("new OpsLlmModelCallExecutor.Input(")),
                () -> assertFalse(client.contains("ChatClient.builder(")),
                () -> assertFalse(client.contains("Future<String>")),
                () -> assertFalse(client.contains("future.get(")),
                () -> assertFalse(client.contains("future.cancel(")),
                () -> assertFalse(client.contains("OpsNodeDeadlineContext.remainingMillis(")),
                () -> assertFalse(client.contains("模型调用开始前节点总时限已耗尽")),
                () -> assertFalse(client.contains("模型调用被中断")),
                () -> assertTrue(client.lines().count() <= 220),
                () -> assertTrue(contentCall.contains("OpsLlmModelCallExecutor modelCallExecutor")),
                () -> assertTrue(contentCall.contains("modelCallExecutor.execute(")),
                () -> assertTrue(contentCall.contains("new OpsLlmModelCallExecutor.Input(")),
                () -> assertTrue(executor.contains("ChatClient.builder(")),
                () -> assertTrue(executor.contains("OpsNodeDeadlineContext.remainingMillis(")),
                () -> assertTrue(executor.contains("Future<T>")),
                () -> assertTrue(executor.contains("OpsModelCallAwaiter.get(submitted.future(), executionTimeoutMillis)")),
                () -> assertTrue(executor.contains("OpsModelCallAwaiter.started(submitted.started(), submitted.future(), queueWaitBudgetMillis)")),
                () -> assertTrue(executor.contains("submitted.future().cancel(true)")),
                () -> assertTrue(executor.contains("OpsLlmTraceContext.withTrace(")),
                () -> assertTrue(executor.contains("Thread.currentThread().interrupt()")),
                () -> assertTrue(executor.contains("cause instanceof RuntimeException")),
                () -> assertTrue(executor.contains("模型调用开始前节点总时限已耗尽")),
                () -> assertTrue(executor.contains("模型调用被中断")),
                () -> assertFalse(executor.contains("@Service")),
                () -> assertFalse(executor.contains("@Value")),
                () -> assertFalse(executor.contains("org.springframework.beans")),
                () -> assertFalse(executor.contains("org.springframework.context")),
                () -> assertTrue(executor.lines().count() <= 155),
                () -> assertTrue(awaiter.contains("Future<T>")),
                () -> assertTrue(awaiter.matches("(?s).*\\.get\\([^,]+,\\s*TimeUnit\\.MILLISECONDS\\).*")),
                () -> assertTrue(awaiter.matches("(?s).*\\.await\\([^,]+,\\s*TimeUnit\\.MILLISECONDS\\).*")),
                () -> assertTrue(awaiter.contains("try (Registration")),
                () -> assertTrue(awaiter.contains("binding.cancelWhileAwaiting(future)")),
                () -> assertTrue(awaiter.contains("listener.close()")),
                () -> assertTrue(awaiter.contains("cancellationRequested(binding.runId())")),
                () -> assertTrue(awaiter.contains("throw new OpsRunCanceledException(binding.runId())")),
                () -> assertTrue(awaiter.contains("InterruptedException | CancellationException")),
                () -> assertFalse(awaiter.contains("Thread.interrupted()")),
                () -> assertTrue(awaiter.contains("started.countDown()")),
                () -> assertFalse(awaiter.contains("@Service")),
                () -> assertFalse(awaiter.contains("@Value")),
                () -> assertTrue(awaiter.lines().count() <= 100),
                () -> assertTrue(control.contains("registry.onCancel(runId")),
                () -> assertTrue(control.contains("future.cancel(true)")),
                () -> assertFalse(control.contains("waiter.interrupt()")),
                () -> assertTrue(control.contains("sink.onDispose(registration::close)")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
