package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationSubAgentDecisionProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void springFacadeDelegatesThinkReviewProtocolAndKeepsFallbackOrdering() throws IOException {
        String facade = read(OPS + "OpsSubAgentDecisionService.java");
        String protocol = read(OPS + "OpsSubAgentDecisionProtocolService.java");
        String mapper = read(OPS + "OpsSubAgentDecisionJsonMapper.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsSubAgentDecisionProtocolService protocolService")),
                () -> assertTrue(facade.contains("protocolService.decide(")),
                () -> assertTrue(facade.contains("protocolService.review(")),
                () -> assertTrue(facade.contains("if (!settings.decisionLlmEnabled())")),
                () -> assertTrue(facade.contains("if (!settings.reflectionLlmEnabled())")),
                () -> assertTrue(facade.contains("OpsSubAgentFallbackDecisionService fallbackDecisionService")),
                () -> assertTrue(facade.contains("fallbackDecisionService.query(")),
                () -> assertTrue(facade.contains("fallbackDecisionService.review(")),
                () -> assertFalse(facade.contains("private OpsSubAgentDecision fallbackDecision(")),
                () -> assertFalse(facade.contains("private OpsAgentReview fallbackReview(")),
                () -> assertFalse(facade.contains("chatJsonObject(")),
                () -> assertFalse(facade.contains("OpsLlmJsonValidator")),
                () -> assertFalse(facade.contains("JSONObject")),
                () -> assertFalse(facade.contains("JSONArray")),
                () -> assertFalse(facade.contains("buildDecisionPrompt(")),
                () -> assertFalse(facade.contains("buildReviewPrompt(")),
                () -> assertFalse(facade.contains("validPromWindow(")),
                () -> assertTrue(facade.lines().count() <= 140),
                () -> assertTrue(protocol.contains("chatJsonObjectWithEagerSkillContext(")),
                () -> assertTrue(protocol.contains("validateSubAgentDecision(")),
                () -> assertTrue(protocol.contains("validateSubAgentReview(")),
                () -> assertTrue(protocol.contains("THINK_SYSTEM_PROMPT")),
                () -> assertTrue(protocol.contains("REVIEW_SYSTEM_PROMPT")),
                () -> assertTrue(protocol.contains("DecisionAttempt(")),
                () -> assertTrue(protocol.contains("ReviewAttempt(")),
                () -> assertTrue(protocol.contains("Status.NO_JSON")),
                () -> assertTrue(protocol.contains("Status.INVALID_SCHEMA")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("org.springframework")),
                () -> assertTrue(protocol.lines().count() <= 260),
                () -> assertTrue(mapper.contains("OpsSubAgentDecision toDecision(")),
                () -> assertTrue(mapper.contains("OpsAgentReview toReview(")),
                () -> assertTrue(mapper.contains("validPromWindow(")),
                () -> assertTrue(mapper.contains("validRetrievalMode(")),
                () -> assertTrue(mapper.contains("validStatus(")),
                () -> assertFalse(mapper.contains("chatJsonObject(")),
                () -> assertFalse(mapper.contains("OpsLlmJsonValidator")),
                () -> assertFalse(mapper.contains("@Service")),
                () -> assertFalse(mapper.contains("org.springframework")),
                () -> assertTrue(mapper.lines().count() <= 130));
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
