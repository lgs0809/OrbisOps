package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolHandlerUniquenessArchitectureTest {

    private static final String REGISTRY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/toolset/OpsLocalToolExecutionHandlerRegistry.java";

    @Test
    void duplicateAdapterKeysMustFailAtRegistryConstruction() throws IOException {
        String registry = Files.readString(projectRoot().resolve(REGISTRY));

        assertAll(
                () -> assertTrue(registry.contains("putIfAbsent(adapterType, handler)")),
                () -> assertTrue(registry.contains("LOCAL_ADAPTER_HANDLER_DUPLICATE")),
                () -> assertTrue(registry.contains("duplicate.getClass().getName()")),
                () -> assertTrue(registry.contains("handler.getClass().getName()")),
                () -> assertTrue(registry.contains("AbstractOpsSingleLocalToolExecutionHandler.normalize")));
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
