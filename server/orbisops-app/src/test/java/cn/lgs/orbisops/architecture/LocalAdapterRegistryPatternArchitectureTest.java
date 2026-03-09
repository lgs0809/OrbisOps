package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAdapterRegistryPatternArchitectureTest {

    private static final String TOOLSET = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void localAdapterExtensionPointMustUseImmutableDuplicateCheckedRegistry() throws IOException {
        String registry = read("OpsLocalToolExecutionHandlerRegistry.java");
        String facade = read("OpsLocalOpsAdapterService.java");

        assertAll(
                () -> assertTrue(registry.contains("Map.copyOf")),
                () -> assertTrue(registry.contains("putIfAbsent")),
                () -> assertTrue(registry.contains("LOCAL_ADAPTER_HANDLER_DUPLICATE")),
                () -> assertTrue(registry.contains("LOCAL_ADAPTER_NOT_IMPLEMENTED")),
                () -> assertFalse(facade.contains("switch (")),
                () -> assertFalse(facade.contains("case \"LOCAL_")));
    }

    private String read(String file) throws IOException {
        return Files.readString(projectRoot().resolve(TOOLSET + file));
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
