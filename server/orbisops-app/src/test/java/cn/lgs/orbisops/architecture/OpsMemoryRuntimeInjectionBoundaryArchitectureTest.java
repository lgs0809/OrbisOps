package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMemoryRuntimeInjectionBoundaryArchitectureTest {

    private static final String MEMORY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void runtimeMemoryFacadeDelegatesTypedLimitAndProjection() throws IOException {
        String service = read(MEMORY + "OpsMemoryRuntimeInjectionService.java");
        String settings = read(MEMORY + "OpsMemoryRuntimeInjectionSettings.java");
        String projector = read(MEMORY + "OpsMemoryRuntimeSelectionProjector.java");
        String configuration = read(APPLICATION + "OpsMemoryRuntimeInjectionConfiguration.java");

        assertAll(
                () -> assertTrue(service.contains("OpsMemoryRuntimeInjectionSettings settings")),
                () -> assertTrue(service.contains("OpsMemoryRuntimeSelectionProjector projector")),
                () -> assertTrue(service.contains("settings.maxInjectionCount()")),
                () -> assertTrue(service.contains("projector.project(")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("StringBuilder")),
                () -> assertFalse(service.contains("MemoryType.PROJECT_FACT")),
                () -> assertFalse(service.contains("LinkedHashMap")),
                () -> assertTrue(service.lines().count() <= 60),
                () -> assertTrue(settings.contains("public record OpsMemoryRuntimeInjectionSettings(")),
                () -> assertTrue(projector.contains("MemoryType.PROJECT_FACT")),
                () -> assertTrue(projector.contains("用户陈述，尚未由工具验证")),
                () -> assertTrue(projector.contains("ref.put(\"memoryHash\"")),
                () -> assertFalse(projector.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.memory.max-injection-count")));
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
