package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionSignalPersistenceArchitectureTest {

    private static final String PORT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/adapter/repository/ISkillEvolutionSignalRepository.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcSkillEvolutionSignalRepository.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionSignalApplicationService.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/OpsSkillEvolutionSignalService.java";
    private static final String MIGRATION = "db/migrations/sql/ops-intent-memory-skill-channel.sql";

    @Test
    void domainPortOnlyExposesTypedSignalAndHintContracts() throws IOException {
        String port = read(PORT);

        assertAll(
                () -> assertTrue(port.contains("interface ISkillEvolutionSignalRepository")),
                () -> assertTrue(port.contains("SkillEvolutionSignalSnapshot saveIdempotent(")),
                () -> assertTrue(port.contains("void saveHintIdempotent(SkillEvolutionHintSnapshot hint)")),
                () -> assertTrue(port.contains("List<SkillEvolutionHintSnapshot> findPendingHints(")),
                () -> assertTrue(port.contains("boolean markHintConsumed(")),
                () -> assertFalse(port.contains("JdbcTemplate")),
                () -> assertFalse(port.contains("Map<String, Object>")),
                () -> assertFalse(port.contains("org.springframework")),
                () -> assertFalse(port.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void jdbcRepositoryOwnsSignalHintSqlAndTypedRowMapping() throws IOException {
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("implements ISkillEvolutionSignalRepository")),
                () -> assertTrue(repository.contains("ObjectProvider<JdbcTemplate>")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_skill_evolution_signal")),
                () -> assertTrue(repository.contains("ON DUPLICATE KEY UPDATE signal_id=signal_id")),
                () -> assertTrue(repository.contains("WHERE idempotency_key=?")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_skill_evolution_hint")),
                () -> assertTrue(repository.contains("WHERE project_id=? AND status='CREATED'")),
                () -> assertTrue(repository.contains("SET status='CONSUMED'")),
                () -> assertTrue(repository.contains("SkillEvolutionSignalSnapshot")),
                () -> assertTrue(repository.contains("SkillEvolutionHintSnapshot")),
                () -> assertFalse(repository.contains("JSON.toJSONString")),
                () -> assertFalse(repository.contains("SkillEvolutionSignalPolicy")),
                () -> assertFalse(repository.contains("OpsConfigAuditService")),
                () -> assertFalse(repository.contains("CREATE TABLE")));
    }

    @Test
    void applicationUsesRepositoryLegacyServiceContainsNoJdbcAndMigrationRemainsAuthority() throws IOException {
        String application = read(APPLICATION);
        String service = read(SERVICE);
        String migration = read(MIGRATION);

        assertAll(
                () -> assertTrue(application.contains("ISkillEvolutionSignalRepository")),
                () -> assertTrue(application.contains("repository.saveIdempotent(")),
                () -> assertTrue(application.contains("repository.saveHintIdempotent(")),
                () -> assertTrue(application.contains("repository.findPendingHints(")),
                () -> assertTrue(application.contains("repository.markHintConsumed(")),
                () -> assertFalse(service.contains("ISkillEvolutionSignalRepository")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("jdbcTemplate.")),
                () -> assertFalse(service.contains("INSERT INTO")),
                () -> assertFalse(service.contains("SELECT ")),
                () -> assertFalse(service.contains("UPDATE ai_ops_skill_evolution_hint")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_signal`")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_hint`")));
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
