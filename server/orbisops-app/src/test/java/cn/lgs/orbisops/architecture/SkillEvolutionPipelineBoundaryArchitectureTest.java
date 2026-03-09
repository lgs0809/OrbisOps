package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionPipelineBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/skill/";
    private static final String DOMAIN =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/skill/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void applicationMustOwnTheProductionPipelinePort() throws IOException {
        String application = read(
                APPLICATION + "SkillEvolutionApplicationService.java");
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/skill/"
                + "OpsSkillEvolutionPipelineAdapter.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/skill/"
                + "OpsSkillCatalogApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(application.contains(
                        "implements SkillEvolutionPipelinePort")),
                () -> assertTrue(application.contains(
                        "SkillEvolutionSignalApplicationService")),
                () -> assertTrue(application.contains(
                        "SkillExperienceApplicationService")),
                () -> assertTrue(application.contains(
                        "SkillPatchCandidateApplicationService")),
                () -> assertTrue(application.contains(
                        "SkillReleaseApplicationService")),
                () -> assertFalse(adapter.contains(
                        "implements SkillEvolutionPipelinePort")),
                () -> assertFalse(adapter.contains(
                        "OpsSkillEvolutionPipelineService")),
                () -> assertTrue(configuration.contains(
                        "public SkillEvolutionApplicationService skillEvolutionApplicationService(")),
                () -> assertTrue(configuration.contains(
                        "SkillEvolutionPipelinePort pipelinePort")));
    }

    @Test
    void applicationPipelineMustRemainFrameworkAndTriggerNeutral()
            throws IOException {
        String application = read(
                APPLICATION + "SkillEvolutionApplicationService.java");
        String ports = read(APPLICATION + "SkillEvolutionAuthoringPort.java")
                + read(APPLICATION + "SkillEvolutionSimilarityPort.java")
                + read(APPLICATION + "SkillEvolutionPipelineAuditPort.java")
                + read(APPLICATION + "SkillEvolutionPromotionSettings.java");
        String payloadCodec = read(APPLICATION + "SkillEvolutionPayloadCodec.java");

        assertAll(
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("@Transactional")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("SkillEvolutionPayloadCodecPort")),
                () -> assertTrue(application.contains("SkillEvolutionPayloadCodec payloadCodecPort")),
                () -> assertFalse(application.contains("@Value")),
                () -> assertFalse(payloadCodec.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(payloadCodec.contains("org.springframework")),
                () -> assertFalse(ports.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(ports.contains("org.springframework")),
                () -> assertFalse(ports.contains("JdbcTemplate")),
                () -> assertFalse(ports.contains("com.alibaba.fastjson")));
    }

    @Test
    void authoringDecisionMustUseTypedPublishedFacts() throws IOException {
        String port = read(APPLICATION + "SkillEvolutionAuthoringPort.java");
        String authored = read(APPLICATION + "SkillEvolutionAuthoredCandidate.java");
        String application = read(APPLICATION + "SkillEvolutionApplicationService.java");
        String coordinator = read(APPLICATION + "SkillEvolutionCandidateCoordinator.java");
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/skill/"
                + "OpsSkillEvolutionPipelineAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("SkillEvolutionAuthoredCandidate author(")),
                () -> assertFalse(port.contains("Map<String, Object> author(")),
                () -> assertTrue(authored.contains("record SkillEvolutionAuthoredCandidate")),
                () -> assertTrue(application.contains("authored.reusableChange()")),
                () -> assertFalse(application.contains("authored.get(")),
                () -> assertFalse(coordinator.contains("authored.get(")),
                () -> assertTrue(adapter.contains("SkillEvolutionAuthoredCandidate.from(")));
    }

    @Test
    void similarityDecisionMustUseTypedPublishedFacts() throws IOException {
        String port = read(APPLICATION + "SkillEvolutionSimilarityPort.java");
        String match = read(APPLICATION + "SkillEvolutionSimilarityMatch.java");
        String application = read(APPLICATION + "SkillEvolutionApplicationService.java");
        String coordinator = read(APPLICATION + "SkillEvolutionCandidateCoordinator.java");
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/skill/"
                + "OpsSkillEvolutionPipelineAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("SkillEvolutionSimilarityMatch bestMatch(")),
                () -> assertFalse(port.contains("Map<String, Object> bestMatch(")),
                () -> assertTrue(match.contains("record SkillEvolutionSimilarityMatch")),
                () -> assertTrue(application.contains("similar.frozen()")),
                () -> assertFalse(application.contains("isFrozen(Map<String, Object>")),
                () -> assertFalse(application.contains("similar.get(")),
                () -> assertFalse(coordinator.contains("similar.get(")),
                () -> assertTrue(adapter.contains("new SkillEvolutionSimilarityMatch(")));
    }

    @Test
    void legacyPipelineServiceMustRemainAMapCompatibilityFacade()
            throws IOException {
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/skill/"
                + "OpsSkillEvolutionPipelineService.java");

        assertAll(
                () -> assertTrue(facade.contains(
                        "private final SkillEvolutionApplicationService applicationService;")),
                () -> assertTrue(facade.contains("applicationService.decide(")),
                () -> assertFalse(facade.contains("OpsSkillEvolutionSignalService")),
                () -> assertFalse(facade.contains("OpsSkillAuthoringAgent")),
                () -> assertFalse(facade.contains("OpsSkillSimilarityService")),
                () -> assertFalse(facade.contains("OpsSkillPatchCandidateService")),
                () -> assertFalse(facade.contains("OpsSkillReleaseService")),
                () -> assertFalse(facade.contains("OpsSkillExperienceService")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("JdbcTemplate")),
                () -> assertTrue(facade.lines().count() < 130));
    }

    @Test
    void opportunityClassificationMustBelongToDomainPolicy()
            throws IOException {
        String policy = read(DOMAIN
                + "service/SkillEvolutionOpportunityPolicy.java");
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/skill/"
                + "OpsSkillOpportunityDetector.java");

        assertAll(
                () -> assertTrue(policy.contains(
                        "public String detect(SkillEvolutionOpportunityInput input)")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertTrue(facade.contains(
                        "SkillEvolutionOpportunityPolicy")),
                () -> assertFalse(facade.contains("hasReusableText(")));
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
