package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillReleaseProcessBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/skill/";

    @Test
    void releaseApplicationServiceMustRemainAStableFacade() throws IOException {
        String service = read(APPLICATION + "SkillReleaseApplicationService.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "private final SkillReleaseStarter starter;")),
                () -> assertTrue(service.contains(
                        "private final SkillReleasePromotionCoordinator promotionCoordinator;")),
                () -> assertTrue(service.contains(
                        "private final SkillReleaseRollbackCoordinator rollbackCoordinator;")),
                () -> assertTrue(service.contains("return startOutcome(candidateId).view();")),
                () -> assertTrue(service.contains("return starter.startOutcome(candidateId);")),
                () -> assertTrue(service.contains("SkillReleaseStartOutcome")),
                () -> assertTrue(service.contains(
                        "promotionCoordinator.promoteIfReady(required(release));")),
                () -> assertTrue(service.contains(
                        "rollbackCoordinator.rollbackIfDegraded(required(release));")),
                () -> assertTrue(service.contains("SkillReleaseSnapshot")),
                () -> assertTrue(service.contains("SkillReleaseStatus.CANARY")),
                () -> assertTrue(service.contains("SkillReleaseStatus.ACTIVE")),
                () -> assertFalse(service.contains("publishEvolvedProjectSkill(")),
                () -> assertFalse(service.contains("rollbackProjectVersion(")),
                () -> assertFalse(service.contains("validationService.validate(")),
                () -> assertFalse(service.contains("shadowService.evaluate(")),
                () -> assertTrue(service.lines().count() < 140));
    }

    @Test
    void releaseComponentsMustOwnSemanticProcessSlices() throws IOException {
        String starter = read(APPLICATION + "SkillReleaseStarter.java");
        String promotion = read(
                APPLICATION + "SkillReleasePromotionCoordinator.java");
        String rollback = read(
                APPLICATION + "SkillReleaseRollbackCoordinator.java");
        String state = read(
                APPLICATION + "SkillReleaseStateCoordinator.java");

        assertAll(
                () -> assertTrue(starter.contains("validationService.validateDecision(")),
                () -> assertTrue(starter.contains("shadowService.evaluateOutcome(")),
                () -> assertTrue(starter.contains("SkillReleaseStartOutcome")),
                () -> assertFalse(starter.contains("validation.get(")),
                () -> assertFalse(starter.contains("shadow.get(")),
                () -> assertTrue(starter.contains("releasePort.create(")),
                () -> assertTrue(promotion.contains("releasePort.claim(")),
                () -> assertTrue(promotion.contains(
                        "publishEvolvedProjectSkillOutcome(")),
                () -> assertTrue(promotion.contains("createProjectSkillOutcome(")),
                () -> assertTrue(promotion.contains("SkillPublicationOutcome published")),
                () -> assertFalse(promotion.contains("published.get(")),
                () -> assertTrue(rollback.contains("recoverProjectRelease(")),
                () -> assertTrue(state.contains("releasePort.complete(")),
                () -> assertTrue(state.contains("expectedStatus")),
                () -> assertTrue(promotion.contains("SkillReleaseStatus.PROMOTING")),
                () -> assertTrue(rollback.contains("SkillReleaseStatus.ROLLING_BACK")),
                () -> assertTrue(state.contains("SkillReleaseSnapshot")),
                () -> assertTrue(state.contains("SkillReleaseStatus")),
                () -> assertFalse(promotion.contains("release.get(")),
                () -> assertFalse(rollback.contains("release.get(")),
                () -> assertFalse(state.contains("release.get(")),
                () -> assertTrue(state.contains("candidateService.updateStatus(")),
                () -> assertTrue(state.contains("auditPort.recordStateChanged(")));
    }

    @Test
    void releaseMetricDecisionMustUseTypedDomainMetrics() throws IOException {
        String port = read(APPLICATION + "SkillEffectMetricPort.java");
        String service = read(APPLICATION + "SkillEffectMetricApplicationService.java");
        String promotion = read(APPLICATION + "SkillReleasePromotionCoordinator.java");
        String rollback = read(APPLICATION + "SkillReleaseRollbackCoordinator.java");
        String jdbc = read("orbisops-infrastructure/src/main/java/"
                + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcSkillEffectMetricAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("SkillEffectMetrics metrics(")),
                () -> assertFalse(port.contains("Map<String, Object> metrics(")),
                () -> assertTrue(service.contains("public SkillEffectMetrics metrics(")),
                () -> assertTrue(promotion.contains("releasePort.canaryEvidence(canary)")),
                () -> assertTrue(promotion.contains("evidence.promotable()")),
                () -> assertTrue(rollback.contains("releasePort.canaryEvidence(active)")),
                () -> assertTrue(rollback.contains("evidence.isolationReason()")),
                () -> assertFalse(promotion.contains("Map<String, Object> metrics")),
                () -> assertFalse(rollback.contains("Map<String, Object> metrics")),
                () -> assertTrue(jdbc.contains("SkillEffectMetrics.from(rows.get(0))")));
    }

    @Test
    void releaseApplicationBoundaryMustRemainFrameworkNeutral() throws IOException {
        String combined = read(APPLICATION + "SkillReleaseApplicationService.java")
                + read(APPLICATION + "SkillReleaseStarter.java")
                + read(APPLICATION + "SkillReleasePromotionCoordinator.java")
                + read(APPLICATION + "SkillReleaseRollbackCoordinator.java")
                + read(APPLICATION + "SkillReleaseStateCoordinator.java");

        assertAll(
                () -> assertFalse(combined.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(combined.contains("org.springframework")),
                () -> assertFalse(combined.contains("JdbcTemplate")),
                () -> assertFalse(combined.contains("@Transactional")),
                () -> assertFalse(combined.contains("com.alibaba.fastjson")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(
                current.resolve("orbisops-application"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(
                parent.resolve("orbisops-application"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(
                nested.resolve("orbisops-application"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
