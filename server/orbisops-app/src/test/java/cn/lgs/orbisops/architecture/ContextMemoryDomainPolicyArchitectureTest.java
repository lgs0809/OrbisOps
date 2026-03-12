package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryDomainPolicyArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/ContextMemoryDefinitionPolicy.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String ADMIN_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryAdminApplicationService.java";
    private static final String QUERY_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryQueryApplicationService.java";
    private static final String SCENE_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemorySceneQueryApplicationService.java";

    @Test
    void domainPolicyOwnsDefinitionsConsistencyConfidenceAndSceneRouting() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("SCOPE_TYPES")),
                () -> assertTrue(policy.contains("MEMORY_TYPES")),
                () -> assertTrue(policy.contains("STATUSES")),
                () -> assertTrue(policy.contains("normalizeScope(")),
                () -> assertTrue(policy.contains("normalizeMemoryType(")),
                () -> assertTrue(policy.contains("normalizeStatus(")),
                () -> assertTrue(policy.contains("validateScopeAndType(")),
                () -> assertTrue(policy.contains("normalizeConfidence(")),
                () -> assertTrue(policy.contains("memoryTypesForScene(")),
                () -> assertTrue(policy.contains("PROJECT_GLOSSARY")),
                () -> assertTrue(policy.contains("USER_PREFERENCE")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(policy.contains("lombok")));
    }

    @Test
    void applicationAndTriggerConsumersDelegateWithoutReimplementingDomainRules() throws IOException {
        String service = read(SERVICE);
        String adminService = read(ADMIN_SERVICE);
        String queryService = read(QUERY_SERVICE);
        String sceneService = read(SCENE_SERVICE);

        assertAll(
                () -> assertFalse(service.contains("ContextMemoryDefinitionPolicy")),
                () -> assertFalse(service.contains("definitionPolicy.normalizeScope(")),
                () -> assertFalse(service.contains("definitionPolicy.normalizeMemoryType(")),
                () -> assertFalse(service.contains("definitionPolicy.normalizeStatus(")),
                () -> assertFalse(service.contains("definitionPolicy.memoryTypesForScene(")),
                () -> assertFalse(service.contains("definitionPolicy.validateScopeAndType(")),
                () -> assertFalse(service.contains("definitionPolicy.normalizeConfidence(")),
                () -> assertTrue(queryService.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(queryService.contains("definitionPolicy.normalizeScope(")),
                () -> assertTrue(queryService.contains("definitionPolicy.normalizeMemoryType(")),
                () -> assertTrue(queryService.contains("definitionPolicy.normalizeStatus(")),
                () -> assertTrue(adminService.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(adminService.contains("definitionPolicy.validateScopeAndType(")),
                () -> assertTrue(adminService.contains("definitionPolicy.normalizeConfidence(")),
                () -> assertTrue(sceneService.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(sceneService.contains("definitionPolicy.memoryTypesForScene(")),
                () -> assertFalse(service.contains("private static final Set<String> SCOPE_TYPES")),
                () -> assertFalse(service.contains("private static final Set<String> MEMORY_TYPES")),
                () -> assertFalse(service.contains("private static final Set<String> STATUSES")),
                () -> assertFalse(service.contains("private String normalizeScope(")),
                () -> assertFalse(service.contains("private String normalizeMemoryType(")),
                () -> assertFalse(service.contains("private String normalizeStatus(")),
                () -> assertFalse(service.contains("private void validateScopeAndType(")),
                () -> assertFalse(service.contains("private BigDecimal clamp(")),
                () -> assertFalse(service.contains("private List<String> sceneMemoryTypes(")),
                () -> assertFalse(service.contains("normalized.contains(\"OPS\")")),
                () -> assertFalse(service.contains("normalized.contains(\"DESIGN\")")));
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
