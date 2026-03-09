package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedMemoryPersistenceArchitectureTest {

    private static final String PORT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/adapter/repository/IGovernedMemoryRepository.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcGovernedMemoryRepository.java";
    private static final String SCHEMA = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcGovernedMemorySchemaInitializer.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/GovernedMemoryApplicationService.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/OpsMemoryStoreService.java";

    @Test
    void domainRepositoryPortOnlyExposesTypedMemoryContracts() throws IOException {
        String port = read(PORT);

        assertAll(
                () -> assertTrue(port.contains("interface IGovernedMemoryRepository")),
                () -> assertTrue(port.contains("Optional<GovernedMemorySnapshot>")),
                () -> assertTrue(port.contains("Optional<GovernedMemoryHead>")),
                () -> assertTrue(port.contains("GovernedMemoryRuntimeQuery")),
                () -> assertTrue(port.contains("GovernedMemoryVersionSnapshot")),
                () -> assertFalse(port.contains("JdbcTemplate")),
                () -> assertFalse(port.contains("org.springframework")),
                () -> assertFalse(port.contains("com.alibaba.fastjson")),
                () -> assertFalse(port.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(port.contains("Map<String, Object> save(")));
    }

    @Test
    void jdbcRepositoryOwnsSqlJsonRowMappingConflictVerifyAndAuditPersistence() throws IOException {
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("implements IGovernedMemoryRepository")),
                () -> assertTrue(repository.contains("ObjectProvider<JdbcTemplate>")),
                () -> assertTrue(repository.contains("SELECT_COLUMNS")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_memory")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_memory_version")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_memory_conflict")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_memory_audit")),
                () -> assertTrue(repository.contains("confidence = GREATEST(confidence, 0.9000)")),
                () -> assertTrue(repository.contains("EXPLICIT_MEMORY_VALUE_CHANGED")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(repository.contains("MemoryScope.require")),
                () -> assertTrue(repository.contains("MemoryType.require")),
                () -> assertFalse(repository.contains("GovernedMemoryPolicy")),
                () -> assertFalse(repository.contains("GovernedMemoryHashPolicy")),
                () -> assertFalse(repository.contains("OpsConfigAuditService")));
    }

    @Test
    void schemaInitializerIsTheOnlyDdlOwnerAndApplicationUsesTypedRepository() throws IOException {
        String schema = read(SCHEMA);
        String application = read(APPLICATION);
        String store = read(STORE);

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory (")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory_version")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory_conflict")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory_audit")),
                () -> assertTrue(application.contains("IGovernedMemoryRepository")),
                () -> assertTrue(application.contains("repository.findByIdempotencyKey(")),
                () -> assertTrue(application.contains("repository.findLatestHead(")),
                () -> assertTrue(application.contains("repository.selectForRuntime(")),
                () -> assertTrue(application.contains("repository.verifyProjectFact(")),
                () -> assertFalse(store.contains("IGovernedMemoryRepository")),
                () -> assertFalse(store.contains("JdbcTemplate")),
                () -> assertFalse(store.contains("queryForList(")),
                () -> assertFalse(store.contains("template.update(")),
                () -> assertFalse(store.contains("template.execute(")),
                () -> assertFalse(store.contains("CREATE TABLE IF NOT EXISTS")),
                () -> assertFalse(store.contains("ensureTables(")),
                () -> assertFalse(store.contains("@PostConstruct")));
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
