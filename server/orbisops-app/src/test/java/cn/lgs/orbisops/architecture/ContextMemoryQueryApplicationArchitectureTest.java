package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryQueryApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryQueryApplicationService.java";
    private static final String QUERY = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryQuery.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsFilterNormalizationCriteriaAndIdentityLookup() throws IOException {
        String service = read(APPLICATION_SERVICE);
        String query = read(QUERY);

        assertAll(
                () -> assertTrue(query.contains("record ContextMemoryQuery")),
                () -> assertTrue(service.contains("ContextMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(service.contains("definitionPolicy.normalizeScope(")),
                () -> assertTrue(service.contains("definitionPolicy.normalizeMemoryType(")),
                () -> assertTrue(service.contains("definitionPolicy.normalizeStatus(")),
                () -> assertTrue(service.contains("new ContextMemorySearchCriteria(")),
                () -> assertTrue(service.contains("storeService.search(")),
                () -> assertTrue(service.contains("storeService.require(memoryId)")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("Map<String, Object>")));
    }

    @Test
    void triggerFacadeDelegatesGenericReadWithoutOwningPolicyOrCriteria() throws IOException {
        String facade = read(FACADE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("ContextMemoryApplicationFacade")),
                () -> assertFalse(facade.contains("ContextMemoryQueryApplicationService")),
                () -> assertTrue(facade.contains("new ContextMemoryQuery(")),
                () -> assertTrue(facade.contains("applicationFacade.search(")),
                () -> assertTrue(facade.contains("requireFacade().require(memoryId)")),
                () -> assertFalse(facade.contains("ContextMemoryDefinitionPolicy")),
                () -> assertFalse(facade.contains("ContextMemorySearchCriteria")),
                () -> assertFalse(facade.contains("normalizeScope(")),
                () -> assertFalse(facade.contains("normalizeMemoryType(")),
                () -> assertFalse(facade.contains("normalizeStatus(")),
                () -> assertTrue(configuration.contains("contextMemoryQueryApplicationService(")),
                () -> assertTrue(configuration.contains("new ContextMemoryDefinitionPolicy()")));
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
