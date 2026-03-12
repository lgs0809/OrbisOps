package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsExecutorBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void executorConfigurationOnlyBindsNamedBeansAndDelegatesThreadPolicy() throws IOException {
        String configuration = read(OPS + "OpsMultiAgentExecutorConfig.java");
        String settings = read(OPS + "OpsExecutorPoolSettings.java");
        String factory = read(OPS + "OpsTraceAwareExecutorFactory.java");

        assertAll(
                () -> assertTrue(configuration.contains("OpsTraceAwareExecutorFactory executorFactory")),
                () -> assertTrue(configuration.contains("OpsExecutorPoolSettings.resolve(")),
                () -> assertTrue(configuration.contains("opsSubAgentExecutor")),
                () -> assertTrue(configuration.contains("opsRunExecutor")),
                () -> assertTrue(configuration.contains("opsModelCallExecutor")),
                () -> assertTrue(configuration.contains("ragIngestionExecutor")),
                () -> assertTrue(configuration.contains("opsMemoryExecutor")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.executor.rejection-policy:AbortPolicy")),
                () -> assertTrue(configuration.contains("\"CallerRunsPolicy\".equalsIgnoreCase(rejectionPolicy)")),
                () -> assertTrue(configuration.contains("backgroundOnlyRejectionPolicy")),
                () -> assertFalse(configuration.contains("orbisops.chat.memory.executor.rejection-policy:CallerRunsPolicy")),
                () -> assertFalse(configuration.contains("TraceContext")),
                () -> assertFalse(configuration.contains("AtomicInteger")),
                () -> assertFalse(configuration.contains("new Thread(")),
                () -> assertFalse(configuration.contains("rejectionHandler(")),
                () -> assertFalse(configuration.contains("class TraceAwareThreadPoolExecutor")),
                () -> assertTrue(configuration.lines().count() <= 110),
                () -> assertTrue(settings.contains("public record OpsExecutorPoolSettings(")),
                () -> assertTrue(settings.contains("public static OpsExecutorPoolSettings resolve(")),
                () -> assertTrue(factory.contains("TraceContext.wrap(")),
                () -> assertTrue(factory.contains("new LinkedBlockingQueue<>(resolved.queueCapacity())")),
                () -> assertTrue(factory.contains("ThreadPoolExecutor.AbortPolicy")),
                () -> assertTrue(factory.contains("ThreadPoolExecutor.CallerRunsPolicy")),
                () -> assertFalse(factory.contains("@Configuration")),
                () -> assertFalse(factory.contains("@Value")));
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
