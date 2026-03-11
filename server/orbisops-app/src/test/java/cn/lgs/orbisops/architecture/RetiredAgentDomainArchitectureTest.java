package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents the retired legacy Agent domain package from re-entering production sources. */
class RetiredAgentDomainArchitectureTest {

    private static final List<String> MODULES = List.of(
            "orbisops-types", "orbisops-domain", "orbisops-application",
            "orbisops-infrastructure", "orbisops-api", "orbisops-trigger", "orbisops-app");

    @Test
    void retiredAgentDomainMustRemainAbsentFromProductionSources() throws IOException {
        Path root = projectRoot();
        Path retiredRoot = root.resolve("orbisops-domain/src/main/java/cn/lgs/orbisops/domain/agent");
        List<String> violations = new ArrayList<>();

        if (Files.isDirectory(retiredRoot)) {
            try (Stream<Path> files = Files.walk(retiredRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(path -> violations.add("retired source exists: " + path));
            }
        }

        for (String module : MODULES) {
            Path sourceRoot = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) continue;
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .toList()) {
                    String source = Files.readString(file);
                    if (source.contains("cn.lgs.orbisops.domain.agent.")) {
                        violations.add(root.relativize(file).toString().replace('\\', '/'));
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () ->
                "Retired Agent domain must not re-enter production sources:\n"
                        + String.join("\n", violations));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        throw new IllegalStateException("Cannot locate OrbisOps reactor root from " + current);
    }
}
