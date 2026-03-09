package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionInputSummaryArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/service/SkillEvolutionInputPolicy.java";
    private static final String INPUT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionInput.java";
    private static final String EVENT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionTraceEvent.java";
    private static final String MESSAGE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionMessage.java";
    private static final String SUMMARY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionInputSummary.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionJobApplicationService.java";
    private static final String INPUT_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionInputAdapter.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionService.java";

    @Test
    void domainOwnsNeutralInputSummaryEvidenceAndAbbreviationRules() throws IOException {
        String policy = read(POLICY);
        String input = read(INPUT);
        String event = read(EVENT);
        String message = read(MESSAGE);
        String summary = read(SUMMARY);

        assertAll(
                () -> assertTrue(input.contains("record SkillEvolutionInput")),
                () -> assertTrue(event.contains("record SkillEvolutionTraceEvent")),
                () -> assertTrue(message.contains("record SkillEvolutionMessage")),
                () -> assertTrue(summary.contains("record SkillEvolutionInputSummary")),
                () -> assertTrue(summary.contains("List<SkillEvolutionEvidenceReference> evidenceReferences")),
                () -> assertTrue(summary.contains("String contextBundleHash")),
                () -> assertTrue(policy.contains("MAX_EVENT_SUMMARIES = 80")),
                () -> assertTrue(policy.contains("MAX_TOOL_EVENTS = 20")),
                () -> assertTrue(policy.contains("MAX_USER_GOAL_LENGTH = 1000")),
                () -> assertTrue(policy.contains("FINAL_EVENT_TYPES")),
                () -> assertTrue(policy.contains("evidenceReferences(")),
                () -> assertTrue(policy.contains("contextBundleHash(")),
                () -> assertTrue(policy.contains("abbreviate(")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(event.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(message.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationDelegatesSummaryRulesAndTriggerAdapterOnlyMapsRuntimeTypes() throws IOException {
        String application = read(APPLICATION);
        String adapter = read(INPUT_ADAPTER);
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(application.contains("SkillEvolutionInputPolicy")),
                () -> assertTrue(application.contains("inputPolicy.summarize(input)")),
                () -> assertTrue(application.contains("new SkillEvolutionInput(")),
                () -> assertTrue(adapter.contains("new SkillEvolutionTraceEvent(")),
                () -> assertTrue(adapter.contains("new SkillEvolutionMessage(")),
                () -> assertFalse(service.contains("SkillEvolutionInputPolicy")),
                () -> assertFalse(service.contains("private EvolutionInput")),
                () -> assertFalse(service.contains("private EvolutionSummary")),
                () -> assertFalse(service.contains("private EvolutionSummary summarize(")),
                () -> assertFalse(service.contains("private String contextBundleHash(")),
                () -> assertFalse(service.contains("private String abbreviate(")),
                () -> assertFalse(service.contains("Set.of(\"FINAL_OUTPUT\"")),
                () -> assertFalse(service.contains(".limit(80)")),
                () -> assertFalse(service.contains(".limit(20)")));
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
