package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAdapterHandlerBoundaryArchitectureTest {

    private static final String TOOLSET =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void localFacadeMustDelegateToImmutableHandlerRegistry() throws IOException {
        String facade = read(TOOLSET + "OpsLocalOpsAdapterService.java");
        String registry = read(TOOLSET + "OpsLocalToolExecutionHandlerRegistry.java");
        String handler = read(TOOLSET + "OpsLocalToolExecutionHandler.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsLocalToolExecutionHandlerRegistry handlerRegistry")),
                () -> assertTrue(facade.contains("handlerRegistry.execute(")),
                () -> assertFalse(facade.contains("switch (adapter")),
                () -> assertFalse(facade.contains("case \"LOCAL_")),
                () -> assertFalse(facade.contains("private final OpsLocalMySqlAdapter")),
                () -> assertTrue(registry.contains("Map.copyOf(indexed)")),
                () -> assertTrue(registry.contains("putIfAbsent(adapterType, handler)")),
                () -> assertTrue(registry.contains("LOCAL_ADAPTER_NOT_IMPLEMENTED")),
                () -> assertTrue(handler.contains("supportedAdapterTypes()")),
                () -> assertTrue(handler.contains("OpsLocalToolArguments arguments")));
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
