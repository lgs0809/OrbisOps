package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInToolsetCatalogBoundaryArchitectureTest {

    private static final String TOOLSET =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void catalogMustBeAThinFacadeAndModuleContributorsMustOwnDefinitions() throws IOException {
        String registry = read(TOOLSET + "OpsToolsetRegistry.java");
        String catalog = read(TOOLSET + "OpsBuiltInToolsetCatalog.java");
        String contributorRegistry = read(
                TOOLSET + "OpsBuiltInToolsetContributorRegistry.java");
        String observability = read(TOOLSET + "ObservabilityToolsetContributor.java");
        String database = read(TOOLSET + "DatabaseToolsetContributor.java");
        String repair = read(TOOLSET + "RepairToolsetContributor.java");

        assertAll(
                () -> assertTrue(registry.contains("OpsBuiltInToolsetCatalog builtInCatalog")),
                () -> assertTrue(registry.contains("builtInCatalog.definitions()")),
                () -> assertFalse(registry.contains("observability.prometheus")),
                () -> assertFalse(registry.contains("db.mysql.readonly")),
                () -> assertFalse(registry.contains("code.repair")),
                () -> assertTrue(catalog.contains("OpsBuiltInToolsetContributorRegistry registry")),
                () -> assertTrue(catalog.contains("registry.definitions()")),
                () -> assertFalse(catalog.contains("ObservabilityToolsetContributor")),
                () -> assertFalse(catalog.contains("DatabaseToolsetContributor")),
                () -> assertFalse(catalog.contains("tools.read(")),
                () -> assertFalse(catalog.contains("targetWrite(")),
                () -> assertTrue(contributorRegistry.contains("List.copyOf(ordered)")),
                () -> assertTrue(contributorRegistry.contains("BUILT_IN_TOOLSET_ID_DUPLICATE")),
                () -> assertTrue(contributorRegistry.contains("BUILT_IN_TOOL_NAME_DUPLICATE")),
                () -> assertTrue(contributorRegistry.contains("BUILT_IN_TOOLSET_DEPENDENCY_CYCLE")),
                () -> assertTrue(observability.contains("observability.prometheus")),
                () -> assertTrue(observability.contains("observability.logs")),
                () -> assertTrue(database.contains("db.mysql.readonly")),
                () -> assertTrue(database.contains("db.mysql.change")),
                () -> assertFalse(database.contains("db.mysql.landing")),
                () -> assertTrue(repair.contains("code.repository")),
                () -> assertTrue(repair.contains("code.repair")),
                () -> assertTrue(catalog.lines().count() < 40));
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
