package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryPersistenceArchitectureTest {

    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String STORE_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryStoreApplicationService.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcContextMemoryRepository.java";
    private static final String INITIALIZER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcContextMemorySchemaInitializer.java";
    private static final String PORT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/adapter/repository/IContextMemoryRepository.java";
    private static final String SNAPSHOT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/ContextMemorySnapshot.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsContextMemoryMapper.java";

    @Test
    void applicationStoreOwnsRepositoryAndTriggerServiceOwnsNoInfrastructure() throws IOException {
        String service = read(SERVICE);
        String storeService = read(STORE_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("ContextMemoryApplicationFacade")),
                () -> assertFalse(service.contains("ContextMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("OpsContextMemoryMapper")),
                () -> assertFalse(service.contains("IContextMemoryRepository")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("ObjectProvider<JdbcTemplate>")),
                () -> assertFalse(service.contains("DataAccessException")),
                () -> assertFalse(service.contains("@Qualifier")),
                () -> assertFalse(service.contains("@PostConstruct")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("CREATE TABLE")),
                () -> assertFalse(service.contains("SELECT ")),
                () -> assertFalse(service.contains("INSERT INTO")),
                () -> assertFalse(service.contains("UPDATE ai_ops_context_memory")),
                () -> assertFalse(service.contains("ai_ops_context_memory")),
                () -> assertFalse(service.contains("private volatile boolean initialized")),
                () -> assertFalse(service.contains("autoInit")),
                () -> assertTrue(storeService.contains("IContextMemoryRepository")),
                () -> assertFalse(storeService.contains("JdbcTemplate")),
                () -> assertFalse(storeService.contains("ai_ops_context_memory")),
                () -> assertFalse(storeService.contains("org.springframework")));
    }

    @Test
    void infrastructureOwnsDmlAndUniqueSchemaInitialization() throws IOException {
        String repository = read(REPOSITORY);
        String initializer = read(INITIALIZER);

        assertAll(
                () -> assertTrue(repository.contains("implements IContextMemoryRepository")),
                () -> assertTrue(repository.contains("SELECT id, memory_id")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_context_memory")),
                () -> assertTrue(repository.contains("UPDATE ai_ops_context_memory")),
                () -> assertFalse(repository.contains("CREATE TABLE IF NOT EXISTS ai_ops_context_memory")),
                () -> assertTrue(initializer.contains("CREATE TABLE IF NOT EXISTS ai_ops_context_memory")),
                () -> assertTrue(initializer.contains("orbisops.context-memory.auto-init")),
                () -> assertFalse(initializer.contains("INSERT INTO ai_ops_context_memory")),
                () -> assertFalse(initializer.contains("SELECT id, memory_id")));
    }

    @Test
    void domainPortAndSnapshotRemainFrameworkNeutral() throws IOException {
        String port = read(PORT);
        String snapshot = read(SNAPSHOT);

        assertAll(
                () -> assertTrue(port.contains("interface IContextMemoryRepository")),
                () -> assertTrue(port.contains("ContextMemorySnapshot")),
                () -> assertTrue(snapshot.contains("record ContextMemorySnapshot")),
                () -> assertFalse(port.contains("org.springframework")),
                () -> assertFalse(port.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(port.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(snapshot.contains("org.springframework")),
                () -> assertFalse(snapshot.contains("lombok")));
    }

    @Test
    void mapperOwnsCompatibilityMapAndKeywordCodecOnly() throws IOException {
        String mapper = read(MAPPER);

        assertAll(
                () -> assertTrue(mapper.contains("ContextMemorySnapshot")),
                () -> assertTrue(mapper.contains("Map<String, Object> data")),
                () -> assertTrue(mapper.contains("JSON.toJSONString")),
                () -> assertFalse(mapper.contains("JdbcTemplate")),
                () -> assertFalse(mapper.contains("ai_ops_context_memory")),
                () -> assertFalse(mapper.contains("IContextMemoryRepository")));
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
