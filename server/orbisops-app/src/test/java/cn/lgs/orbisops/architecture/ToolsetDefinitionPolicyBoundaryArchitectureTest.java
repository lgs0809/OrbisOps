package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsetDefinitionPolicyBoundaryArchitectureTest {

    private static final String TOOLSET =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/toolset/";
    private static final String DOMAIN =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/toolset/model/";

    @Test
    void toolConstructionMustUseTypedProviderAndSemanticsFactories() throws IOException {
        String registry = read(TOOLSET + "OpsToolsetRegistry.java");
        String catalog = read(TOOLSET + "OpsBuiltInToolsetCatalog.java");
        String tools = read(TOOLSET + "OpsToolDefinitionFactory.java");
        String toolsets = read(TOOLSET + "OpsToolsetDefinitionFactory.java");
        String semantics = read(DOMAIN + "ToolSemantics.java");
        String provider = read(DOMAIN + "ToolProviderDescriptor.java");

        assertAll(
                () -> assertFalse(registry.contains("OpsToolDefinition.builder()")),
                () -> assertFalse(registry.contains("riskLevel(")),
                () -> assertFalse(catalog.contains("OpsToolDefinition.builder()")),
                () -> assertFalse(catalog.contains("riskLevel(")),
                () -> assertTrue(tools.contains("OpsToolDefinition.builder()")),
                () -> assertTrue(tools.contains("ToolProviderDescriptor provider")),
                () -> assertTrue(tools.contains("ToolSemantics semantics")),
                () -> assertTrue(tools.contains("ToolSemantics.readOnlyTool()")),
                () -> assertTrue(tools.contains("ToolSemantics.validation()")),
                () -> assertTrue(tools.contains("ToolSemantics.repairWorkspaceWrite()")),
                () -> assertTrue(tools.contains("ToolSemantics.workflowCommand()")),
                () -> assertTrue(tools.contains("ToolSemantics.targetResourceWrite()")),
                () -> assertFalse(tools.contains("boolean readOnly,")),
                () -> assertFalse(tools.contains("boolean writesTargetResource,")),
                () -> assertTrue(semantics.contains("TARGET_WRITE_GOVERNANCE_REQUIRED")),
                () -> assertTrue(semantics.contains("RETRY_SAFE_REQUIRES_IDEMPOTENT")),
                () -> assertTrue(provider.contains("Credentials and live clients are intentionally excluded")),
                () -> assertTrue(toolsets.contains("OpsToolsetDefinition.builder()")),
                () -> assertTrue(toolsets.contains("sourceType(\"BUILT_IN\")")),
                () -> assertTrue(toolsets.contains("approved ChangePackage")),
                () -> assertFalse(toolsets.contains("OpsToolDefinition.builder()")));
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
