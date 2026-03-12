package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryStoreApplicationArchitectureTest {

    private static final String STORE_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryStoreApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String AUDIT_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsContextMemoryAuditAdapter.java";
    private static final String FAILURE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsContextMemoryStoreFailureAdapter.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsStoreAvailabilityMutationAuditAndProjectionIsolation() throws IOException {
        String service = read(STORE_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("IContextMemoryRepository")),
                () -> assertTrue(service.contains("ContextMemoryProjectionPolicy")),
                () -> assertTrue(service.contains("ContextMemoryAuditPort")),
                () -> assertTrue(service.contains("ContextMemoryStoreFailurePort")),
                () -> assertTrue(service.contains("repository.available()")),
                () -> assertTrue(service.contains("target.exists(required.memoryId())")),
                () -> assertTrue(service.contains("target.upsert(required)")),
                () -> assertTrue(service.contains("target.updateStatus(memoryId, status)")),
                () -> assertTrue(service.contains("projectionPolicy.project(item)")),
                () -> assertTrue(service.contains("observeFailure(\"projection-upsert\"")),
                () -> assertTrue(service.contains("auditPort.record(")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("JdbcTemplate")));
    }

    @Test
    void triggerFacadeOnlyParsesCompatibilityMapsAndDelegatesStoreUseCases() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("ContextMemoryApplicationFacade")),
                () -> assertFalse(facade.contains("ContextMemoryStoreApplicationService")),
                () -> assertFalse(facade.contains("ContextMemoryAdminApplicationService")),
                () -> assertFalse(facade.contains("ContextMemoryQueryApplicationService")),
                () -> assertFalse(facade.contains("ContextMemorySceneQueryApplicationService")),
                () -> assertTrue(facade.contains("applicationFacade.search(")),
                () -> assertTrue(facade.contains("requireFacade().require(")),
                () -> assertTrue(facade.contains("requireFacade().create(")),
                () -> assertTrue(facade.contains("requireFacade().update(")),
                () -> assertTrue(facade.contains("requireFacade().updateStatus(")),
                () -> assertTrue(facade.contains("applicationFacade.saveExtractedItems(items)")),
                () -> assertFalse(facade.contains("IContextMemoryRepository")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("ObjectProvider")),
                () -> assertFalse(facade.contains("ContextMemoryProjectionPolicy")),
                () -> assertFalse(facade.contains("repository.available()")),
                () -> assertFalse(facade.contains("repository.upsert(")),
                () -> assertFalse(facade.contains("catch (RuntimeException")),
                () -> assertFalse(facade.contains("Context Memory 审计写入失败")),
                () -> assertFalse(facade.contains("同步 Context Memory 失败")),
                () -> assertFalse(facade.contains("@Slf4j")),
                () -> assertFalse(facade.contains("log.debug")));
    }

    @Test
    void triggerAdaptersOwnRuntimeAuditAndFailureDiagnosticsOnly() throws IOException {
        String auditAdapter = read(AUDIT_ADAPTER);
        String failureAdapter = read(FAILURE_ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(auditAdapter.contains("implements ContextMemoryAuditPort")),
                () -> assertTrue(auditAdapter.contains("OpsConfigAuditService")),
                () -> assertTrue(auditAdapter.contains("recordRuntimeEvent(")),
                () -> assertFalse(auditAdapter.contains("IContextMemoryRepository")),
                () -> assertFalse(auditAdapter.contains("JdbcTemplate")),
                () -> assertTrue(failureAdapter.contains("implements ContextMemoryStoreFailurePort")),
                () -> assertTrue(failureAdapter.contains("log.debug")),
                () -> assertFalse(failureAdapter.contains("IContextMemoryRepository")),
                () -> assertTrue(configuration.contains("contextMemoryStoreApplicationService(")),
                () -> assertTrue(configuration.contains("new ContextMemoryProjectionPolicy()")));
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
