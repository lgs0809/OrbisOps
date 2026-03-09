package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsetRegistryProjectionBoundaryArchitectureTest {

    private static final String TOOLSET =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void registryMustOnlyQueryCatalogAndDelegateDeepCopyProjection() throws IOException {
        String registry = read(TOOLSET + "OpsToolsetRegistry.java");
        String copier = read(TOOLSET + "OpsToolsetDefinitionCopier.java");

        assertAll(
                () -> assertTrue(registry.contains("OpsBuiltInToolsetCatalog builtInCatalog")),
                () -> assertTrue(registry.contains("OpsToolsetDefinitionCopier copier")),
                () -> assertTrue(registry.contains("map(copier::copy)")),
                () -> assertTrue(registry.contains("listBuiltInToolsets()")),
                () -> assertTrue(registry.contains("listEffectiveToolsets(")),
                () -> assertTrue(registry.contains("findTool(")),
                () -> assertTrue(registry.contains("filter(OpsToolsetDefinition::isEnabled)")),
                () -> assertFalse(registry.contains("legacyLocalLanding")),
                () -> assertTrue(registry.contains("externalLocalProviders::allowsToolset")),
                () -> assertFalse(registry.contains("OpsToolsetDefinition.builder()")),
                () -> assertFalse(registry.contains("OpsToolDefinition.builder()")),
                () -> assertFalse(registry.contains("new ArrayList<>(source.getTags())")),
                () -> assertFalse(registry.contains("copyTool(")),
                () -> assertFalse(registry.contains("riskLevel(")),
                () -> assertFalse(registry.contains("sourceType(\"BUILT_IN\")")),
                () -> assertTrue(registry.lines().count() <= 130),
                () -> assertTrue(copier.contains("OpsToolsetDefinition.builder()")),
                () -> assertTrue(copier.contains("OpsToolDefinition.builder()")),
                () -> assertTrue(copier.contains("new ArrayList<>(source.getTags())")),
                () -> assertTrue(copier.contains("source.getTools().stream().map(this::copyTool).toList()")),
                () -> assertTrue(copier.contains("writesRepairWorkspace(source.isWritesRepairWorkspace())")),
                () -> assertTrue(copier.contains("writesTargetResource(source.isWritesTargetResource())")),
                () -> assertTrue(copier.contains("requiresChangePackage(source.isRequiresChangePackage())")),
                () -> assertTrue(copier.contains("requiresApproval(source.isRequiresApproval())")),
                () -> assertTrue(copier.contains("outputSchemaJson(source.getOutputSchemaJson())")),
                () -> assertTrue(copier.contains("outputBudgetJson(source.getOutputBudgetJson())")),
                () -> assertFalse(copier.contains("@Service")),
                () -> assertFalse(copier.contains("observability.prometheus")),
                () -> assertFalse(copier.contains("listEffectiveToolsets(")),
                () -> assertFalse(copier.contains("findTool(")));
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
