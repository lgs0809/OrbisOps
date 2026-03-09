package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillGovernanceStateBoundaryArchitectureTest {

    private static final String DOMAIN_MODEL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/";
    private static final String DOMAIN_REPOSITORY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/adapter/repository/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";

    @Test
    void criticalSkillGovernanceMustRemainTypedAndUseDedicatedCasBoundary() throws IOException {
        String state = read(DOMAIN_MODEL + "SkillGovernanceState.java");
        String lifecycle = read(DOMAIN_MODEL + "SkillLifecycleStatus.java");
        String mutationMode = read(DOMAIN_MODEL + "SkillMutationMode.java");
        String executionMode = read(DOMAIN_MODEL + "SkillExecutionMode.java");
        String bindingMode = read(DOMAIN_MODEL + "SkillBindingMode.java");
        String lock = read(DOMAIN_MODEL + "SkillLock.java");
        String repository = read(DOMAIN_REPOSITORY + "ISkillCatalogRepository.java");
        String genericMutation = read(APPLICATION + "SkillCatalogMutationUseCase.java");
        String governanceMutation = read(APPLICATION + "SkillGovernanceTransitionUseCase.java");

        assertAll(
                () -> assertTrue(state.contains("SkillLifecycleStatus lifecycleStatus")),
                () -> assertTrue(state.contains("SkillMutationMode mutationMode")),
                () -> assertTrue(state.contains("SkillExecutionMode executionMode")),
                () -> assertTrue(state.contains("SkillBindingMode bindingMode")),
                () -> assertTrue(state.contains("SkillLock lock")),
                () -> assertTrue(lifecycle.contains("RETIRED")),
                () -> assertTrue(mutationMode.contains("LOCKED")),
                () -> assertTrue(mutationMode.contains("SEALED")),
                () -> assertTrue(executionMode.contains("SHADOW_ONLY")),
                () -> assertTrue(executionMode.contains("QUARANTINED")),
                () -> assertTrue(bindingMode.contains("PINNED")),
                () -> assertTrue(lock.contains("approvalId")),
                () -> assertTrue(repository.contains("compareAndSetGovernance(SkillGovernanceUpdate update)")),
                () -> assertTrue(genericMutation.contains("requireNoCriticalGovernanceMutation")),
                () -> assertTrue(genericMutation.contains("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED")),
                () -> assertTrue(governanceMutation.contains("compareAndSetGovernance")),
                () -> assertFalse(governanceMutation.contains("Map<String, Object>")));
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
