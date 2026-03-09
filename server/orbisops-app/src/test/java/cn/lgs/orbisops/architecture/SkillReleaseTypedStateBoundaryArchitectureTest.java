package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillReleaseTypedStateBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/";

    @Test
    void releasePersistenceAndStateMachineMustRemainTyped() throws IOException {
        String port = read(APPLICATION + "SkillReleasePort.java");
        String service = read(APPLICATION + "SkillReleaseApplicationService.java");
        String starter = read(APPLICATION + "SkillReleaseStarter.java");
        String promotion = read(APPLICATION + "SkillReleasePromotionCoordinator.java");
        String rollback = read(APPLICATION + "SkillReleaseRollbackCoordinator.java");
        String state = read(APPLICATION + "SkillReleaseStateCoordinator.java");
        String canary = read(APPLICATION + "SkillCanaryContextApplicationService.java");
        String status = read(DOMAIN + "SkillReleaseStatus.java");

        assertAll(
                () -> assertTrue(port.contains("Optional<SkillReleaseSnapshot> findByCandidate")),
                () -> assertTrue(port.contains("List<SkillReleaseSnapshot> listEvaluable")),
                () -> assertTrue(port.contains("SkillReleaseStatus fromStatus")),
                () -> assertFalse(port.contains("Map<String, Object>")),
                () -> assertTrue(service.contains("SkillReleaseSnapshot")),
                () -> assertTrue(starter.contains("SkillReleaseStartOutcome")),
                () -> assertTrue(starter.contains("validateDecision(")),
                () -> assertTrue(starter.contains("evaluateOutcome(")),
                () -> assertFalse(starter.contains("validation.get(")),
                () -> assertFalse(starter.contains("shadow.get(")),
                () -> assertFalse(promotion.contains("release.get(")),
                () -> assertFalse(rollback.contains("release.get(")),
                () -> assertFalse(state.contains("release.get(")),
                () -> assertTrue(canary.contains("SkillCanaryCandidateSnapshot")),
                () -> assertTrue(canary.contains("SkillFrozenCandidateSnapshot")),
                () -> assertTrue(status.contains("requireTransitionTo(")),
                () -> assertTrue(status.contains("reconciliationRequiredStatus(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
