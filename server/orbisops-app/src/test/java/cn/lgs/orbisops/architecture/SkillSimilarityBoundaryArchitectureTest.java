package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSimilarityBoundaryArchitectureTest {

    private static final String SKILL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/";

    @Test
    void facadeMustDelegateSettingsSignaturesScoringProjectionAndCatalogMatching() throws IOException {
        String facade = read(SKILL + "OpsSkillSimilarityService.java");
        String settings = read(SKILL + "OpsSkillSimilaritySettings.java");
        String metrics = read(SKILL + "OpsSkillSimilarityTextMetrics.java");
        String signatures = read(SKILL + "OpsSkillSimilaritySignatureFactory.java");
        String scorer = read(SKILL + "OpsSkillSimilarityScorer.java");
        String projector = read(SKILL + "OpsSkillSimilarityMatchProjector.java");
        String coordinator = read(SKILL + "OpsSkillSimilarityMatchCoordinator.java");
        String configuration = read(APPLICATION + "OpsSkillSimilarityConfiguration.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsSkillSimilarityMatchCoordinator matchCoordinator")),
                () -> assertTrue(facade.contains("legacyConstructorDefaults()")),
                () -> assertTrue(facade.contains("ObjectProvider<OpsSkillSemanticMatcher>")),
                () -> assertTrue(facade.contains("matchCoordinator.bestMatch(")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("@Autowired(required = false)")),
                () -> assertFalse(facade.contains("SkillRoutingProfilePolicy")),
                () -> assertFalse(facade.contains("JSON.toJSONString")),
                () -> assertFalse(facade.contains("double dice(")),
                () -> assertFalse(facade.contains("listProjectSkills(")),
                () -> assertTrue(facade.lines().count() <= 65),
                () -> assertTrue(settings.contains("public record OpsSkillSimilaritySettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(metrics.contains("double dice(")),
                () -> assertTrue(metrics.contains("double jaccard(")),
                () -> assertTrue(metrics.contains("String normalize(")),
                () -> assertFalse(metrics.contains("@Service")),
                () -> assertTrue(signatures.contains("SkillRoutingProfilePolicy")),
                () -> assertTrue(signatures.contains("JSON.toJSONString")),
                () -> assertTrue(signatures.contains("CandidateContext candidate(")),
                () -> assertTrue(signatures.contains("SkillContext skill(")),
                () -> assertTrue(signatures.contains("SKILL_ROUTING_PROFILE_REQUIRED")),
                () -> assertFalse(signatures.contains("@Service")),
                () -> assertTrue(scorer.contains("semanticMatcher.similarity(")),
                () -> assertTrue(scorer.contains("lexical * 0.20D")),
                () -> assertTrue(scorer.contains("lexical * 0.30D")),
                () -> assertFalse(scorer.contains("SkillCatalogQueryService")),
                () -> assertFalse(scorer.contains("@Service")),
                () -> assertTrue(projector.contains("boolean isFrozen(")),
                () -> assertTrue(projector.contains("Math.round(match.score() * 10_000D)")),
                () -> assertTrue(projector.contains("record Match(")),
                () -> assertFalse(projector.contains("@Service")),
                () -> assertTrue(coordinator.contains("catalogQueryService.listProjectSkills(")),
                () -> assertTrue(coordinator.contains("catalogQueryService.listGlobalSkills()")),
                () -> assertTrue(coordinator.contains("FROZEN_SIMILARITY_GUARD")),
                () -> assertTrue(coordinator.contains("BEST_PROJECT_MATCH")),
                () -> assertTrue(coordinator.contains("settings.threshold()")),
                () -> assertFalse(coordinator.contains("@Service")),
                () -> assertTrue(coordinator.lines().count() <= 120),
                () -> assertTrue(configuration.contains("orbisops.skill-evolution.similarity-threshold")));
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
