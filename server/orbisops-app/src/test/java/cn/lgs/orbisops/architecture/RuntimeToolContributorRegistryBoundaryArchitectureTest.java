package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeToolContributorRegistryBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void runtimeToolContributorsMustUseTypedOrderedImmutableRegistry() throws IOException {
        String contract = read("OpsRuntimeToolContributor.java");
        String registry = read("OpsRuntimeToolContributorRegistry.java");
        String facade = read("OpsRuntimeBuiltInToolContributor.java");

        assertAll(
                () -> assertTrue(contract.contains("String id()")),
                () -> assertTrue(contract.contains("int order()")),
                () -> assertTrue(contract.contains("OpsRuntimeToolContributorRequirement requirement()")),
                () -> assertTrue(contract.contains("void contribute(OpsRuntimeResourceContext context)")),
                () -> assertTrue(registry.contains("List<OpsRuntimeToolContributor> contributors")),
                () -> assertTrue(registry.contains("Map.copyOf(indexed)")),
                () -> assertTrue(registry.contains("List.copyOf(ordered)")),
                () -> assertTrue(registry.contains("RUNTIME_TOOL_CONTRIBUTOR_DUPLICATE")),
                () -> assertTrue(registry.contains("validateNoDuplicateTools")),
                () -> assertTrue(registry.contains("RUNTIME_TOOL_METADATA_OVERWRITE")),
                () -> assertTrue(facade.contains("contributorRegistry.contribute(context)")),
                () -> assertFalse(facade.contains("context.getTools().add")));
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
