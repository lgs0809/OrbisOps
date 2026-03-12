package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextToolsetPublishedLanguageArchitectureTest {

    @Test
    void runtimeContextMustConsumeTypedToolsetPublishedLanguage() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/runtime/contextbundle/RuntimeContextToolsetPort.java");
        String create = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/runtime/contextbundle/RuntimeContextBundleCreateApplicationService.java");
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/runtime/OpsRuntimeContextToolsetAdapter.java");
        String policy = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/runtime/contextbundle/service/RuntimeContextBundlePolicy.java");

        assertAll(
                () -> assertTrue(port.contains("List<RuntimeContextToolsetSnapshot> listEffective")),
                () -> assertFalse(port.contains("List<Map<String, Object>>")),
                () -> assertTrue(create.contains("List<RuntimeContextToolsetSnapshot> effectiveToolsets")),
                () -> assertTrue(create.contains("policy.toolsetRefs(effectiveToolsets)")),
                () -> assertTrue(create.contains("policy.policyRefs(effectiveToolsets)")),
                () -> assertTrue(adapter.contains("ToolsetApplicationService")),
                () -> assertTrue(adapter.contains("new RuntimeContextToolsetSnapshot(")),
                () -> assertTrue(adapter.contains("new RuntimeContextToolPolicySnapshot(")),
                () -> assertFalse(adapter.contains("RuntimeContextBundlePolicy")),
                () -> assertFalse(adapter.contains("toolsetHash")),
                () -> assertFalse(adapter.contains("policyHash")),
                () -> assertTrue(policy.contains("toolsetRefs(")),
                () -> assertTrue(policy.contains("RuntimeContextToolsetSnapshot")),
                () -> assertTrue(policy.contains("RuntimeContextToolPolicySnapshot")));
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
