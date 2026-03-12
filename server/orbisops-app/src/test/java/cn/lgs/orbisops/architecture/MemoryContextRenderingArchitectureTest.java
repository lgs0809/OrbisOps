package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryContextRenderingArchitectureTest {

    private static final String RENDERING_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryContextRenderingApplicationService.java";
    private static final String REQUEST = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryContextRenderingRequest.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsSectionRenderingLimitsAndTrim() throws IOException {
        String service = read(RENDERING_SERVICE);
        String request = read(REQUEST);

        assertAll(
                () -> assertTrue(service.contains("### 用户/项目长期语境")),
                () -> assertTrue(service.contains("### 运维上下文记忆")),
                () -> assertTrue(service.contains("### 运维对话上下文")),
                () -> assertTrue(service.contains("contextMemoryLimit()")),
                () -> assertTrue(service.contains("itemLimit()")),
                () -> assertTrue(service.contains("messageLimit()")),
                () -> assertTrue(service.contains("abbreviate(")),
                () -> assertTrue(service.contains("trimContext(")),
                () -> assertTrue(service.contains("memory context compressed")),
                () -> assertTrue(request.contains("MemoryItemCandidate")),
                () -> assertTrue(request.contains("MemoryMessageCandidate")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("OpsMemoryTextUtils")));
    }

    @Test
    void facadeDelegatesUnifiedQueryWithoutOwningRendering() throws IOException {
        String facade = read(FACADE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("MemoryQueryApplicationService")),
                () -> assertFalse(facade.contains("MemoryContextRenderingApplicationService")),
                () -> assertFalse(facade.contains("MemoryContextRenderingRequest")),
                () -> assertFalse(facade.contains("MemoryContextRenderingResult")),
                () -> assertFalse(facade.contains("private String renderContext(")),
                () -> assertFalse(facade.contains("### 用户/项目长期语境")),
                () -> assertFalse(facade.contains("### 运维上下文记忆")),
                () -> assertFalse(facade.contains("### 运维对话上下文")),
                () -> assertFalse(facade.contains("compressor.trimContext(")),
                () -> assertFalse(facade.contains("OpsMemoryTextUtils.abbreviate(")),
                () -> assertTrue(configuration.contains("memoryContextRenderingApplicationService(")));
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
