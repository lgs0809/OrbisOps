package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPatchCandidateTypedBoundaryArchitectureTest {

    @Test
    void candidateLifecycleAndStableFactsMustRemainTyped() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillPatchCandidatePort.java");
        String service = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillPatchCandidateApplicationService.java");
        String validation = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillPatchValidationApplicationService.java");
        String shadow = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillShadowApplicationService.java");
        String starter = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillReleaseStarter.java");
        String promotion = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillReleasePromotionCoordinator.java");
        String assembler = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillReleasePackageAssembler.java");
        String adapter = read("orbisops-infrastructure/src/main/java/"
                + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcSkillPatchCandidateAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("SkillPatchCandidate create(SkillPatchCandidate candidate)")),
                () -> assertTrue(port.contains("SkillPatchCandidate get(String candidateId)")),
                () -> assertTrue(port.contains("SkillPatchCandidateStatus fromStatus")),
                () -> assertFalse(port.contains("Map<String, Object>")),
                () -> assertFalse(port.contains("String fromStatus")),
                () -> assertTrue(service.contains("SkillPatchCandidate createCandidate(")),
                () -> assertTrue(service.contains("SkillPatchCandidate getCandidate(")),
                () -> assertTrue(validation.contains("candidateService.getCandidate(")),
                () -> assertTrue(validation.contains("validationPolicy.evaluate(\n                candidate,")),
                () -> assertTrue(shadow.contains("candidateService.getCandidate(")),
                () -> assertTrue(starter.contains("SkillPatchCandidate candidate")),
                () -> assertTrue(promotion.contains("SkillPatchCandidate candidate")),
                () -> assertTrue(assembler.contains("patch(SkillPatchCandidate candidate)")),
                () -> assertFalse(starter.contains("candidate.get(")),
                () -> assertFalse(promotion.contains("candidate.get(")),
                () -> assertTrue(adapter.contains("SkillPatchCandidateStatus.require(")),
                () -> assertTrue(adapter.contains("SkillPatchRiskLevel.require(")),
                () -> assertFalse(adapter.contains("candidate.get(")));
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
