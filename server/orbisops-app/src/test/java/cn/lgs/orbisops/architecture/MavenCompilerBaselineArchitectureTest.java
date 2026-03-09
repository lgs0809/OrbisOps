package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenCompilerBaselineArchitectureTest {

    @Test
    void java17CompilationUsesParentManagedCompilerAndReleaseContract() throws IOException {
        String pom = Files.readString(projectRoot().resolve("pom.xml"));

        assertAll(
                () -> assertTrue(pom.contains("<artifactId>maven-compiler-plugin</artifactId>")),
                () -> assertTrue(pom.contains("<release>${java.version}</release>")),
                () -> assertFalse(pom.contains("<version>3.0</version>")),
                () -> assertFalse(pom.contains("<source>${java.version}</source>")),
                () -> assertFalse(pom.contains("<target>${java.version}</target>")));
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
