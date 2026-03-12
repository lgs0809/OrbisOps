package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextCanaryPublishedLanguageArchitectureTest {

    @Test
    void runtimeContextMustConsumeTypedCanaryPublishedLanguage() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/runtime/contextbundle/RuntimeContextCanarySkillPort.java");
        String skillService = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillCanaryContextApplicationService.java");
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/runtime/OpsRuntimeContextCanarySkillAdapter.java");
        String policy = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/runtime/contextbundle/service/RuntimeContextBundlePolicy.java");

        assertAll(
                () -> assertTrue(port.contains("List<RuntimeContextCanarySkillSnapshot> resolve(")),
                () -> assertFalse(port.contains("Map<String, Object>")),
                () -> assertTrue(skillService.contains("List<SkillCanaryCandidateSnapshot> resolveCandidates(")),
                () -> assertTrue(adapter.contains("SkillCanaryContextApplicationService")),
                () -> assertTrue(adapter.contains("new RuntimeContextCanarySkillSnapshot(")),
                () -> assertFalse(adapter.contains("OpsSkillReleaseService")),
                () -> assertFalse(adapter.contains("Map<String, Object>")),
                () -> assertTrue(policy.contains("List<RuntimeContextCanarySkillSnapshot> canaryRefs")),
                () -> assertTrue(policy.contains("RuntimeContextCanarySkillSnapshot::canonicalView")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
