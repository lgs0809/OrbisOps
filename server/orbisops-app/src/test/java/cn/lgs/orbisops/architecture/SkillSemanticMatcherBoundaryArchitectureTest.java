package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSemanticMatcherBoundaryArchitectureTest {

    private static final String SKILL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/";

    @Test
    void matcherMustDelegateTypedSettingsPolicyCacheAndEmbeddingCoordinator() throws IOException {
        String matcher = read(SKILL + "OpsSkillSemanticMatcher.java");
        String settings = read(SKILL + "OpsSkillSemanticSettings.java");
        String policy = read(SKILL + "OpsSkillSemanticPolicy.java");
        String cache = read(SKILL + "OpsSkillEmbeddingCache.java");
        String coordinator = read(SKILL + "OpsSkillEmbeddingScoreCoordinator.java");
        String configuration = read(APPLICATION + "OpsSkillSemanticConfiguration.java");

        assertAll(
                () -> assertTrue(matcher.contains("OpsSkillSemanticSettings settings")),
                () -> assertTrue(matcher.contains("OpsSkillEmbeddingScoreCoordinator scoreCoordinator")),
                () -> assertTrue(matcher.contains("OpsSkillSemanticSettings.defaults()")),
                () -> assertTrue(matcher.contains("OpsSkillRetrievalHttpClient")),
                () -> assertTrue(matcher.contains("scoreCoordinator.scores(")),
                () -> assertFalse(matcher.contains("@Value")),
                () -> assertFalse(matcher.contains("LinkedHashMap")),
                () -> assertFalse(matcher.contains("removeEldestEntry")),
                () -> assertFalse(matcher.contains("model.embed(")),
                () -> assertFalse(matcher.contains("double cosine(")),
                () -> assertTrue(matcher.lines().count() <= 80),
                () -> assertTrue(settings.contains("public record OpsSkillSemanticSettings(")),
                () -> assertTrue(settings.contains("candidateLimit")),
                () -> assertTrue(settings.contains("cacheSize")),
                () -> assertTrue(policy.contains("List<OpsSkillSemanticMatcher.SkillDocument> bounded(")),
                () -> assertTrue(policy.contains("String cacheKey(")),
                () -> assertTrue(policy.contains("double cosine(")),
                () -> assertFalse(policy.contains("@Service")),
                () -> assertTrue(cache.contains("new LinkedHashMap<>(128, 0.75F, true)")),
                () -> assertTrue(cache.contains("removeEldestEntry")),
                () -> assertTrue(cache.contains("synchronized boolean contains")),
                () -> assertFalse(cache.contains("@Service")),
                () -> assertTrue(coordinator.contains("model.embed(query, true)")),
                () -> assertTrue(coordinator.contains("cache.put(")),
                () -> assertTrue(coordinator.contains("policy.cosine(")),
                () -> assertFalse(coordinator.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.skill-runtime.semantic-enabled")),
                () -> assertTrue(configuration.contains("orbisops.skill-runtime.semantic-candidate-limit")),
                () -> assertTrue(configuration.contains("orbisops.skill-runtime.embedding-cache-size")));
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
