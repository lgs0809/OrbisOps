package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemorySceneQueryApplicationArchitectureTest {

    private static final String QUERY = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemorySceneQuery.java";
    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemorySceneQueryApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsSceneRoutingLimitsScopeQueriesAndDedup() throws IOException {
        String query = read(QUERY);
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(query.contains("record ContextMemorySceneQuery")),
                () -> assertTrue(query.contains("Math.max(1, Math.min(limit, 12))")),
                () -> assertTrue(service.contains("ContextMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(service.contains("memoryTypesForScene(query.scene())")),
                () -> assertTrue(service.contains("int perType = Math.max(2")),
                () -> assertTrue(service.contains("type.startsWith(\"USER_\")")),
                () -> assertTrue(service.contains("type.startsWith(\"PROJECT_\")")),
                () -> assertTrue(service.contains("new ContextMemorySearchCriteria(")),
                () -> assertTrue(service.contains("new LinkedHashMap<>()")),
                () -> assertTrue(service.contains("selected.put(memory.memoryId(), memory)")),
                () -> assertTrue(service.contains("limit(query.limit())")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("Map<String, Object>")));
    }

    @Test
    void triggerFacadeDelegatesWithoutOwningSceneAggregation() throws IOException {
        String facade = read(FACADE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("ContextMemoryApplicationFacade")),
                () -> assertFalse(facade.contains("ContextMemorySceneQueryApplicationService")),
                () -> assertTrue(facade.contains("new ContextMemorySceneQuery(")),
                () -> assertTrue(facade.contains("applicationFacade.queryScene(")),
                () -> assertFalse(facade.contains("memoryTypesForScene(scene)")),
                () -> assertFalse(facade.contains("int perType")),
                () -> assertFalse(facade.contains("type.startsWith(\"USER_\")")),
                () -> assertFalse(facade.contains("type.startsWith(\"PROJECT_\")")),
                () -> assertFalse(facade.contains("new LinkedHashMap<>()")),
                () -> assertFalse(facade.contains("result.put(")),
                () -> assertFalse(facade.contains("stream().limit(safeLimit)")),
                () -> assertTrue(configuration.contains("contextMemorySceneQueryApplicationService(")),
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
