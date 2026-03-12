package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLegacyFrozenMigrationArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";

    @Test
    void legacyFrozenRowsMustMigrateFailClosedAndRequireExplicitClassification() throws IOException {
        String state = read(DOMAIN + "SkillGovernanceState.java");
        String initializer = read(INFRASTRUCTURE + "JdbcSkillCatalogSchemaInitializer.java");
        String repository = read(INFRASTRUCTURE + "JdbcSkillCatalogRepository.java");
        String transitions = read(APPLICATION + "SkillGovernanceTransitionUseCase.java");

        assertAll(
                () -> assertTrue(state.contains("SkillMutationMode.LOCKED")),
                () -> assertTrue(state.contains("SkillExecutionMode.QUARANTINED")),
                () -> assertTrue(state.contains("SkillLockType.LEGACY_UNCLASSIFIED")),
                () -> assertTrue(state.contains("legacy FROZEN classification required")),
                () -> assertTrue(initializer.contains("backfillLegacyGovernance()")),
                () -> assertTrue(initializer.contains("mutation_mode='LOCKED'")),
                () -> assertTrue(initializer.contains("execution_mode='QUARANTINED'")),
                () -> assertTrue(initializer.contains("legacy_frozen_classification_required=1")),
                () -> assertTrue(repository.contains("invalidGovernanceState")),
                () -> assertTrue(repository.contains("SYSTEM_GOVERNANCE_READER")),
                () -> assertTrue(transitions.contains("SkillLockType.STABILITY_LOCK")),
                () -> assertTrue(transitions.contains("SkillLockType.INCIDENT_QUARANTINE")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
