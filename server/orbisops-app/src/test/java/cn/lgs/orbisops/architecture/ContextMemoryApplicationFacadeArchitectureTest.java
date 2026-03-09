package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryApplicationFacadeArchitectureTest {

    private static final String APPLICATION_FACADE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryApplicationFacade.java";
    private static final String TRIGGER_FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationFacadeIsSingleTypedEntryForAllContextMemoryUseCases() throws IOException {
        String facade = read(APPLICATION_FACADE);

        assertAll(
                () -> assertTrue(facade.contains("ContextMemoryStoreApplicationService")),
                () -> assertTrue(facade.contains("ContextMemoryQueryApplicationService")),
                () -> assertTrue(facade.contains("ContextMemoryAdminApplicationService")),
                () -> assertTrue(facade.contains("ContextMemorySceneQueryApplicationService")),
                () -> assertTrue(facade.contains("search(ContextMemoryQuery query)")),
                () -> assertTrue(facade.contains("require(String memoryId)")),
                () -> assertTrue(facade.contains("create(ContextMemoryMutationCommand command)")),
                () -> assertTrue(facade.contains("update(String memoryId, ContextMemoryMutationCommand command)")),
                () -> assertTrue(facade.contains("updateStatus(String memoryId, String status)")),
                () -> assertTrue(facade.contains("queryScene(ContextMemorySceneQuery query)")),
                () -> assertTrue(facade.contains("saveExtractedItems(List<ColdMemoryItemSnapshot> items)")),
                () -> assertFalse(facade.contains("org.springframework")),
                () -> assertFalse(facade.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(facade.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(facade.contains("Map<String, Object>")),
                () -> assertFalse(facade.contains("JdbcTemplate")));
    }

    @Test
    void triggerFacadeDependsOnOneApplicationFacadeAndCompatibilityMappersOnly() throws IOException {
        String trigger = read(TRIGGER_FACADE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(trigger.contains("ContextMemoryApplicationFacade")),
                () -> assertTrue(trigger.contains("OpsContextMemoryMapper")),
                () -> assertTrue(trigger.contains("OpsContextMemoryCommandMapper")),
                () -> assertTrue(trigger.contains("applicationFacade.search(")),
                () -> assertTrue(trigger.contains("requireFacade().require(")),
                () -> assertTrue(trigger.contains("requireFacade().create(")),
                () -> assertTrue(trigger.contains("requireFacade().update(")),
                () -> assertTrue(trigger.contains("requireFacade().updateStatus(")),
                () -> assertTrue(trigger.contains("applicationFacade.queryScene(")),
                () -> assertTrue(trigger.contains("applicationFacade.saveExtractedItems(")),
                () -> assertFalse(trigger.contains("ContextMemoryStoreApplicationService")),
                () -> assertFalse(trigger.contains("ContextMemoryQueryApplicationService")),
                () -> assertFalse(trigger.contains("ContextMemoryAdminApplicationService")),
                () -> assertFalse(trigger.contains("ContextMemorySceneQueryApplicationService")),
                () -> assertFalse(trigger.contains("IContextMemoryRepository")),
                () -> assertFalse(trigger.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(configuration.contains("contextMemoryApplicationFacade(")),
                () -> assertTrue(configuration.contains("new ContextMemoryApplicationFacade(")));
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
