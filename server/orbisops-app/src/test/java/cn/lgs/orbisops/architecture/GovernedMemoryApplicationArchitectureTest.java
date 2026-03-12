package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedMemoryApplicationArchitectureTest {

    private static final String SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/GovernedMemoryApplicationService.java";
    private static final String HASH_POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/GovernedMemoryHashPolicy.java";
    private static final String AUDIT_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/GovernedMemoryExternalAuditPort.java";
    private static final String AUDIT_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsGovernedMemoryExternalAuditAdapter.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsGovernedMemoryMapper.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/OpsMemoryStoreService.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationOwnsIdempotencyVersionConflictPersistenceAuditAndVerificationOrder() throws IOException {
        String service = read(SERVICE);
        String hashPolicy = read(HASH_POLICY);
        String auditPort = read(AUDIT_PORT);

        assertAll(
                () -> assertTrue(service.contains("IGovernedMemoryRepository")),
                () -> assertTrue(service.contains("repository.findByIdempotencyKey(")),
                () -> assertTrue(service.contains("repository.findLatestHead(")),
                () -> assertTrue(service.contains("memoryPolicy.nextVersion(")),
                () -> assertTrue(service.contains("hashPolicy.memoryHash(draft, version)")),
                () -> assertTrue(service.contains("memoryIdSupplier.get()")),
                () -> assertTrue(service.contains("clock.instant()")),
                () -> assertTrue(service.contains("repository.insert(pending)")),
                () -> assertTrue(service.contains("repository.appendVersion(")),
                () -> assertTrue(service.contains("repository.markConflict(")),
                () -> assertTrue(service.contains("repository.recordConflict(")),
                () -> assertTrue(service.contains("repository.recordAudit(")),
                () -> assertTrue(service.contains("externalAuditPort.recordCreate(")),
                () -> assertTrue(service.contains("repository.verifyProjectFact(")),
                () -> assertTrue(hashPolicy.contains("memoryHash(GovernedMemoryDraft draft, int version)")),
                () -> assertTrue(hashPolicy.contains("CanonicalObjectHasher.sha256(Map.of(")),
                () -> assertTrue(auditPort.contains("interface GovernedMemoryExternalAuditPort")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("UUID")),
                () -> assertFalse(service.contains("Instant.now(")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("JdbcTemplate")));
    }

    @Test
    void triggerAdaptersOwnConfigAuditAndLegacyMapProjection() throws IOException {
        String audit = read(AUDIT_ADAPTER);
        String mapper = read(MAPPER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(configuration.contains("() -> \"memory-\" + UUID.randomUUID()")),
                () -> assertTrue(configuration.contains("Clock.systemDefaultZone()")),
                () -> assertFalse(configuration.contains("GovernedMemoryCanonicalSnapshotPort")),
                () -> assertFalse(configuration.contains("GovernedMemoryIdPort")),
                () -> assertFalse(configuration.contains("GovernedMemoryClockPort")),
                () -> assertTrue(audit.contains("implements GovernedMemoryExternalAuditPort")),
                () -> assertTrue(audit.contains("OpsConfigAuditService")),
                () -> assertTrue(audit.contains("auditService.record(")),
                () -> assertTrue(mapper.contains("GovernedMemoryCreateCommand createCommand(")),
                () -> assertTrue(mapper.contains("GovernedMemoryVerifyCommand verifyCommand(")),
                () -> assertTrue(mapper.contains("GovernedMemoryRuntimeQuery runtimeQuery(")),
                () -> assertTrue(mapper.contains("Map<String, Object> creationView(")),
                () -> assertTrue(mapper.contains("proofRefsJson")),
                () -> assertFalse(mapper.contains("IGovernedMemoryRepository")),
                () -> assertFalse(mapper.contains("GovernedMemoryPolicy")));
    }

    @Test
    void legacyStoreOnlyMapsDelegatesAndPreservesTypeErrorCompatibility() throws IOException {
        String store = read(STORE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(store.contains("GovernedMemoryApplicationService")),
                () -> assertTrue(store.contains("OpsGovernedMemoryMapper")),
                () -> assertTrue(store.contains("applicationService.create(command)")),
                () -> assertTrue(store.contains("applicationService.require(memoryId)")),
                () -> assertTrue(store.contains("applicationService.selectForRuntime(")),
                () -> assertTrue(store.contains("applicationService.verifyProjectFact(")),
                () -> assertTrue(store.contains("Memory 类型不允许：")),
                () -> assertFalse(store.contains("IGovernedMemoryRepository")),
                () -> assertFalse(store.contains("GovernedMemoryPolicy")),
                () -> assertFalse(store.contains("GovernedMemoryHashPolicy")),
                () -> assertFalse(store.contains("JSON.toJSONString")),
                () -> assertFalse(store.contains("UUID.randomUUID")),
                () -> assertFalse(store.contains("Instant.now")),
                () -> assertFalse(store.contains("OpsConfigAuditService")),
                () -> assertFalse(store.contains("new GovernedMemorySnapshot(")),
                () -> assertTrue(configuration.contains("governedMemoryApplicationService(")),
                () -> assertTrue(configuration.contains("new GovernedMemoryApplicationService(")));
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
