package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInToolsetContributorPatternArchitectureTest {

    private static final String TOOLSET = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void builtInToolsetExtensionPointMustUseOrderedImmutableContributorRegistry() throws IOException {
        String contract = read("OpsBuiltInToolsetContributor.java");
        String registry = read("OpsBuiltInToolsetContributorRegistry.java");
        String catalog = read("OpsBuiltInToolsetCatalog.java");

        assertAll(
                () -> assertTrue(contract.contains("String contributorId()")),
                () -> assertTrue(contract.contains("int order()")),
                () -> assertTrue(contract.contains("List<OpsToolsetDefinition> definitions()")),
                () -> assertTrue(registry.contains("List.copyOf(ordered)")),
                () -> assertTrue(registry.contains("BUILT_IN_TOOLSET_CONTRIBUTOR_DUPLICATE")),
                () -> assertTrue(registry.contains("BUILT_IN_TOOLSET_ID_DUPLICATE")),
                () -> assertTrue(registry.contains("BUILT_IN_TOOL_NAME_DUPLICATE")),
                () -> assertTrue(registry.contains("BUILT_IN_TOOLSET_DEPENDENCY_CYCLE")),
                () -> assertTrue(registry.contains("comparingInt(OpsBuiltInToolsetContributor::order)")),
                () -> assertTrue(catalog.contains("registry.definitions()")),
                () -> assertFalse(catalog.contains("observability.prometheus")),
                () -> assertFalse(catalog.contains("switch (")));
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
