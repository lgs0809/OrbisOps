package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySceneClassificationDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/MemorySceneClassificationPolicy.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String QUERY_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryQueryApplicationService.java";

    @Test
    void domainPolicyOwnsScenePriorityAndKeywordRules() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("explicitScene")),
                () -> assertTrue(policy.contains("taskType")),
                () -> assertTrue(policy.contains("告警")),
                () -> assertTrue(policy.contains("故障")),
                () -> assertTrue(policy.contains("文档")),
                () -> assertTrue(policy.contains("报告")),
                () -> assertTrue(policy.contains("方案")),
                () -> assertTrue(policy.contains("设计")),
                () -> assertTrue(policy.contains("OPS_TROUBLESHOOTING")),
                () -> assertTrue(policy.contains("DOCUMENT_WRITING")),
                () -> assertTrue(policy.contains("DESIGN_DISCUSSION")),
                () -> assertTrue(policy.contains("CHAT")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(policy.contains("lombok")));
    }

    @Test
    void queryServiceDelegatesClassificationAndFacadeOwnsNoPolicy() throws IOException {
        String facade = read(FACADE);
        String queryService = read(QUERY_SERVICE);

        assertAll(
                () -> assertTrue(facade.contains("MemoryQueryApplicationService")),
                () -> assertFalse(facade.contains("MemorySceneClassificationPolicy")),
                () -> assertFalse(facade.contains("scenePolicy.classify(")),
                () -> assertFalse(facade.contains("private String scene(")),
                () -> assertFalse(facade.contains("告警")),
                () -> assertFalse(facade.contains("故障")),
                () -> assertFalse(facade.contains("排障")),
                () -> assertFalse(facade.contains("文档")),
                () -> assertFalse(facade.contains("报告")),
                () -> assertFalse(facade.contains("方案")),
                () -> assertFalse(facade.contains("设计")),
                () -> assertFalse(facade.contains("OPS_TROUBLESHOOTING")),
                () -> assertFalse(facade.contains("DOCUMENT_WRITING")),
                () -> assertFalse(facade.contains("DESIGN_DISCUSSION")),
                () -> assertTrue(queryService.contains("MemorySceneClassificationPolicy")),
                () -> assertTrue(queryService.contains("scenePolicy.classify(")));
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
