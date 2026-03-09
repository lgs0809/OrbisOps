package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolHandlerRegistryBoundaryArchitectureTest {

    private static final String TOOLSET = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void facadeAndHandlersMustRemainSeparatedByRegistry() throws IOException {
        String facade = read("OpsLocalOpsAdapterService.java");
        String registry = read("OpsLocalToolExecutionHandlerRegistry.java");
        String contract = read("OpsLocalToolExecutionHandler.java");
        String mysqlHandler = read("OpsMySqlLocalToolExecutionHandler.java");

        assertAll(
                () -> assertTrue(contract.contains("interface OpsLocalToolExecutionHandler")),
                () -> assertTrue(contract.contains("Set<String> supportedAdapterTypes()")),
                () -> assertTrue(registry.contains("List<OpsLocalToolExecutionHandler> handlers")),
                () -> assertTrue(registry.contains("Map<String, OpsLocalToolExecutionHandler> handlers")),
                () -> assertTrue(registry.contains("this.handlers = Map.copyOf(indexed)")),
                () -> assertTrue(facade.contains("new OpsLocalToolArguments(arguments)")),
                () -> assertFalse(facade.contains("return switch")),
                () -> assertTrue(mysqlHandler.contains("extends AbstractOpsSingleLocalToolExecutionHandler")),
                () -> assertTrue(mysqlHandler.contains("adapter.execute(toolName, arguments)")));
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
