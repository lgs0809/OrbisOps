package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillExecutionModeBoundaryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";

    @Test
    void runtimeSelectionMustUseLifecycleAndExecutionInsteadOfMutationMode() throws IOException {
        String state = read(DOMAIN + "SkillGovernanceState.java");
        String candidate = read(DOMAIN + "SkillRuntimeCandidate.java");
        String snapshot = read(APPLICATION + "SkillCatalogSnapshot.java");
        String assembler = read(APPLICATION + "SkillRuntimeCandidateAssembler.java");
        String selection = read(APPLICATION + "SelectRuntimeSkillsQuery.java");
        String access = read(APPLICATION + "SkillRuntimeCatalogAccess.java");

        assertAll(
                () -> assertTrue(state.contains("lifecycleStatus == SkillLifecycleStatus.ACTIVE")),
                () -> assertTrue(state.contains("executionMode.formalRuntimeEnabled()")),
                () -> assertTrue(candidate.contains("return governanceState.activeAtUse();")),
                () -> assertFalse(candidate.contains("!\"FROZEN\".equals")),
                () -> assertTrue(snapshot.contains("return runtimeCandidate.activeAtUse();")),
                () -> assertTrue(assembler.contains("governancePolicy.governanceState(skill)")),
                () -> assertTrue(access.contains("c.activeAtUse()")),
                () -> assertTrue(access.contains("now.scope().equals(c.scope())")),
                () -> assertTrue(selection.contains("access.retain(request.projectId()")),
                () -> assertFalse(selection.contains("updateMode")),
                () -> assertFalse(selection.contains("mutationMode")));
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
