package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsetRefreshExtractionDecisionArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/";

    @Test
    void singleMcpRefreshSourceMustRemainExplicitWithoutPrematureStrategy() throws IOException {
        Path sourceRoot = projectRoot().resolve(TRIGGER);
        String catalog = Files.readString(sourceRoot.resolve(
                "cn/lgs/orbisops/trigger/ops/toolset/OpsToolsetCatalogService.java"));
        String adapter = Files.readString(sourceRoot.resolve(
                "cn/lgs/orbisops/trigger/application/toolset/OpsToolsetCatalogAdapter.java"));
        String controller = Files.readString(sourceRoot.resolve(
                "cn/lgs/orbisops/trigger/http/admin/OpsToolsetAdminController.java"));
        String allSources;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            allSources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::readUnchecked)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        assertAll(
                () -> assertTrue(catalog.contains("refreshMcpToolset(")),
                () -> assertTrue(catalog.contains("MCP discovery is currently the only stable long-lived Toolset refresh source")),
                () -> assertTrue(catalog.contains("Extract a ToolsetRefresher strategy")),
                () -> assertTrue(adapter.contains("service.refreshMcpToolset(")),
                () -> assertTrue(controller.contains("refreshMcpToolset(")),
                () -> assertFalse(allSources.contains("interface ToolsetRefresher")),
                () -> assertFalse(allSources.contains("enum ToolsetRefreshSource")),
                () -> assertFalse(allSources.contains("class OpenApiToolsetRefresher")),
                () -> assertFalse(allSources.contains("class LocalAdapterToolsetRefresher")),
                () -> assertEquals(3, occurrences(allSources, "refreshMcpToolset(")));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
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
