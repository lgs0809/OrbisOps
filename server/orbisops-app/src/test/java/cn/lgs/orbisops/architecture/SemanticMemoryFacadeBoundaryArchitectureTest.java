package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryFacadeBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void pgVectorFacadeMustDependOnlyOnApplicationFacadeAndMapper()
            throws IOException {
        String source = read(RUNTIME + "OpsPgVectorSemanticMemoryStore.java");

        assertAll(
                () -> assertTrue(source.contains("SemanticMemoryApplicationFacade")),
                () -> assertTrue(source.contains("OpsSemanticMemoryMapper")),
                () -> assertTrue(source.contains("applicationFacade.write(")),
                () -> assertTrue(source.contains("applicationFacade.search(")),
                () -> assertTrue(source.contains("applicationFacade.clear(")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("VectorStore")),
                () -> assertFalse(source.contains("ObjectProvider")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertTrue(source.contains("OpsSemanticMemoryRetrievalSettings settings")),
                () -> assertTrue(source.contains("@Autowired")),
                () -> assertFalse(source.contains("@Value")),
                () -> assertFalse(source.contains("private final int semanticTopK")),
                () -> assertFalse(source.contains("vectorTableName")),
                () -> assertTrue(source.lines().count() < 100));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
