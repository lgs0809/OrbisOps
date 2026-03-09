package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryAdminApplicationArchitectureTest {

    private static final String ADMIN_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryAdminApplicationService.java";
    private static final String COMMAND = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryMutationCommand.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsContextMemoryCommandMapper.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsIdentityPartialMergeValidationAndTypedSnapshotAssembly() throws IOException {
        String service = read(ADMIN_SERVICE);
        String command = read(COMMAND);

        assertAll(
                () -> assertTrue(command.contains("record ContextMemoryMutationCommand")),
                () -> assertTrue(command.contains("boolean confidencePresent")),
                () -> assertTrue(service.contains("ContextMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(service.contains("Supplier<String> identitySupplier")),
                () -> assertTrue(service.contains("generatedMemoryId()")),
                () -> assertTrue(service.contains("ctx-mem-")),
                () -> assertTrue(service.contains("requireStore().require(")),
                () -> assertTrue(service.contains("value(command.scopeType()")),
                () -> assertTrue(service.contains("value(command.title()")),
                () -> assertTrue(service.contains("definitionPolicy.validateScopeAndType(")),
                () -> assertTrue(service.contains("command.confidencePresent()")),
                () -> assertTrue(service.contains("definitionPolicy.normalizeConfidence(")),
                () -> assertFalse(service.contains("Map<String, Object>")),
                () -> assertFalse(service.contains("UUID")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")));
    }

    @Test
    void triggerMapperOwnsMapPresenceKeywordsAndConfidenceParsing() throws IOException {
        String mapper = read(MAPPER);

        assertAll(
                () -> assertTrue(mapper.contains("ContextMemoryMutationCommand command(")),
                () -> assertTrue(mapper.contains("request.containsKey(key)")),
                () -> assertTrue(mapper.contains("safe.containsKey(\"keywords\")")),
                () -> assertTrue(mapper.contains("contextMemoryMapper.keywords(")),
                () -> assertTrue(mapper.contains("safe.containsKey(\"confidence\")")),
                () -> assertTrue(mapper.contains("new BigDecimal(")),
                () -> assertTrue(mapper.contains("catch (NumberFormatException")),
                () -> assertFalse(mapper.contains("ContextMemoryStoreApplicationService")),
                () -> assertFalse(mapper.contains("ContextMemoryDefinitionPolicy")),
                () -> assertFalse(mapper.contains("IContextMemoryRepository")));
    }

    @Test
    void triggerFacadeDelegatesAdminWithoutOwningMutationAssembly() throws IOException {
        String facade = read(FACADE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("ContextMemoryApplicationFacade")),
                () -> assertFalse(facade.contains("ContextMemoryAdminApplicationService")),
                () -> assertTrue(facade.contains("OpsContextMemoryCommandMapper")),
                () -> assertTrue(facade.contains("requireFacade().create(commandMapper.command(request))")),
                () -> assertTrue(facade.contains("requireFacade().update(memoryId, commandMapper.command(request))")),
                () -> assertTrue(facade.contains("requireFacade().updateStatus(memoryId, status)")),
                () -> assertFalse(facade.contains("UUID")),
                () -> assertFalse(facade.contains("ContextMemorySnapshot")),
                () -> assertFalse(facade.contains("requiredText(")),
                () -> assertFalse(facade.contains("private BigDecimal decimal(")),
                () -> assertFalse(facade.contains("NumberFormatException")),
                () -> assertFalse(facade.contains("mapper.keywords(request.get(")),
                () -> assertFalse(facade.contains("new LinkedHashMap<>(mapper.view(before))")),
                () -> assertTrue(configuration.contains("contextMemoryAdminApplicationService(")),
                () -> assertTrue(configuration.contains("new ContextMemoryDefinitionPolicy()")),
                () -> assertTrue(configuration.contains("UUID.randomUUID().toString()")));
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
