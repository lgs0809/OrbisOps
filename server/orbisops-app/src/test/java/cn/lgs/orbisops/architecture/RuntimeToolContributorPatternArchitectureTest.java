package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeToolContributorPatternArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void runtimeToolExtensionPointMustUseOrderedImmutableContributorRegistry() throws IOException {
        String registry = read("OpsRuntimeToolContributorRegistry.java");
        String facade = read("OpsRuntimeBuiltInToolContributor.java");

        assertAll(
                () -> assertTrue(registry.contains("List.copyOf(ordered)")),
                () -> assertTrue(registry.contains("Map.copyOf(indexed)")),
                () -> assertTrue(registry.contains("RUNTIME_TOOL_CONTRIBUTOR_DUPLICATE")),
                () -> assertTrue(registry.contains("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_MISSING")),
                () -> assertTrue(registry.contains("comparingInt(OpsRuntimeToolContributor::order)")),
                () -> assertTrue(facade.contains("contributorRegistry.contribute(context)")),
                () -> assertFalse(facade.contains("contributeRepairTools(")),
                () -> assertFalse(facade.contains("contributeChannelTool(")));
    }

    private String read(String file) throws IOException {
        return Files.readString(projectRoot().resolve(RUNTIME + file));
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
