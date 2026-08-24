package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionJobPersistenceArchitectureTest {

    private static final String PORT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/adapter/repository/ISkillEvolutionJobRepository.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcSkillEvolutionJobRepository.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionJobApplicationService.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionService.java";
    private static final String MIGRATION = "db/migrations/sql/ops-skill-evolution.sql";

    @Test
    void typedRepositoryOwnsJobPatchSqlCasMappingAndTransactions() throws IOException {
        String port = read(PORT);
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertTrue(port.contains("interface ISkillEvolutionJobRepository")),
                () -> assertTrue(port.contains("Optional<SkillEvolutionJobSnapshot> claimPending")),
                () -> assertTrue(port.contains("Optional<SkillEvolutionPatchSnapshot> complete")),
                () -> assertTrue(port.contains("rescheduleOrFail(")),
                () -> assertTrue(repository.contains("implements ISkillEvolutionJobRepository")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_skill_evolution_job")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_skill_evolution_patch")),
                () -> assertTrue(repository.contains("AND j.attempts = :attempts")),
                () -> assertTrue(repository.contains("next_run_at")),
                () -> assertTrue(repository.contains("this::job")),
                () -> assertTrue(repository.contains("this::patch")),
                () -> assertTrue(repository.contains("mysqlTransactionManager")),
                () -> assertFalse(repository.contains("CREATE TABLE")));
    }

    @Test
    void applicationUsesRepositoryWhileTriggerHasNoJdbcSqlDdlOrSchemaLifecycle() throws IOException {
        String application = read(APPLICATION);
        String service = read(SERVICE);
        String migration = read(MIGRATION);

        assertAll(
                () -> assertTrue(application.contains("ISkillEvolutionJobRepository")),
                () -> assertTrue(application.contains("repository.enqueue(")),
                () -> assertTrue(application.contains("repository.claimPending(")),
                () -> assertTrue(application.contains("repository.complete(job, patch, terminalStatus)")),
                () -> assertTrue(application.contains("repository.complete(")),
                () -> assertTrue(application.contains("repository.rescheduleOrFail(")),
                () -> assertFalse(service.contains("ISkillEvolutionJobRepository")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("ObjectProvider")),
                () -> assertFalse(service.contains("DataAccessException")),
                () -> assertFalse(service.contains("@PostConstruct")),
                () -> assertFalse(service.contains("ensureTables(")),
                () -> assertFalse(service.contains("CREATE TABLE")),
                () -> assertFalse(service.contains("ai_ops_skill_evolution_job")),
                () -> assertFalse(service.contains("ai_ops_skill_evolution_patch")),
                () -> assertFalse(service.contains("rs.get")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_job`")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_patch`")));
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
