package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChatRuntimeActionBoundaryArchitectureTest {

    private static final String OPS_APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void chatRuntimeHandlerMustDelegateSyncControlSettingsAndTimeoutProjection() throws IOException {
        String handler = read(OPS_APPLICATION + "OpsChatRuntimeActionHandler.java");
        String coordinator = read(OPS_APPLICATION + "OpsChatSyncExecutionCoordinator.java");
        String timeoutFactory = read(OPS_APPLICATION + "OpsChatTimeoutResponseFactory.java");
        String settings = read(OPS_APPLICATION + "OpsChatRuntimeSettings.java");
        String configuration = read(OPS_APPLICATION + "OpsChatRuntimeConfiguration.java");

        assertAll(
                () -> assertTrue(handler.contains("OpsChatSyncExecutionCoordinator syncExecutionCoordinator")),
                () -> assertTrue(handler.contains("syncExecutionCoordinator.execute(")),
                () -> assertTrue(handler.contains("completed -> promoteLateCompletion(payload.request(), completed)")),
                () -> assertTrue(handler.contains("OpsChatRuntimeSettings.defaults()")),
                () -> assertTrue(handler.contains("@Autowired")),
                () -> assertFalse(handler.contains("@Value")),
                () -> assertFalse(handler.contains("CompletableFuture")),
                () -> assertFalse(handler.contains("TimeoutException")),
                () -> assertFalse(handler.contains("ExecutionException")),
                () -> assertFalse(handler.contains("Thread.currentThread()")),
                () -> assertFalse(handler.contains("OpsRuntimeEvent.builder()")),
                () -> assertFalse(handler.contains("OpsAgentChatResponse.builder()")),
                () -> assertTrue(handler.lines().count() <= 85),
                () -> assertTrue(coordinator.contains("CompletableFuture.supplyAsync(")),
                () -> assertTrue(coordinator.contains("future.get(settings.syncTimeoutSeconds(), TimeUnit.SECONDS)")),
                () -> assertTrue(coordinator.contains("WORK_SESSION_INTERRUPTED")),
                () -> assertTrue(coordinator.contains("WORK_SESSION_EXECUTION_FAILED")),
                () -> assertTrue(coordinator.contains("graphEvents.publishRunEvent(")),
                () -> assertTrue(coordinator.contains("timeoutResponseFactory.create(request)")),
                () -> assertFalse(coordinator.contains("org.springframework")),
                () -> assertTrue(timeoutFactory.contains("OpsRuntimeEvent.builder()")),
                () -> assertTrue(timeoutFactory.contains("OpsAgentChatResponse.builder()")),
                () -> assertTrue(timeoutFactory.contains("agenticWorkSessionStarted")),
                () -> assertTrue(timeoutFactory.contains("没有被取消")),
                () -> assertFalse(timeoutFactory.contains("org.springframework")),
                () -> assertTrue(settings.contains("public record OpsChatRuntimeSettings(")),
                () -> assertTrue(settings.contains("public static OpsChatRuntimeSettings defaults()")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(configuration.contains("@Configuration")),
                () -> assertTrue(configuration.contains("OpsChatRuntimeSettings opsChatRuntimeSettings(")),
                () -> assertTrue(configuration.contains("${orbisops.chat.sync-timeout-seconds:90}")));
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
