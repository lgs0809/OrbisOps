package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionJobAdaptersArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/";

    @Test
    void applicationPortsAndPipelineModelsRemainFrameworkNeutral() throws IOException {
        List<String> files = List.of(
                "SkillEvolutionTraceInputPort.java",
                "SkillEvolutionChatInputPort.java",
                "SkillEvolutionPipelinePort.java",
                "SkillEvolutionPipelineRequest.java",
                "SkillEvolutionPipelineDecision.java",
                "SkillEvolutionAuthoringPort.java",
                "SkillEvolutionSimilarityPort.java",
                "SkillEvolutionPipelineAuditPort.java",
                "SkillEvolutionAuditPort.java");

        for (String file : files) {
            String source = read(APPLICATION + file);
            assertAll(
                    () -> assertFalse(source.contains("cn.lgs.orbisops.trigger"), file),
                    () -> assertFalse(source.contains("org.springframework"), file),
                    () -> assertFalse(source.contains("com.alibaba.fastjson"), file),
                    () -> assertFalse(source.contains("JdbcTemplate"), file),
                    () -> assertFalse(source.contains("UUID.randomUUID"), file),
                    () -> assertFalse(source.contains("Instant.now()"), file));
        }
        String payloadCodec = read(APPLICATION + "SkillEvolutionPayloadCodec.java");
        String patchEncoder = read(APPLICATION + "SkillEvolutionPatchJsonEncoder.java");
        assertAll(
                () -> assertTrue(payloadCodec.contains("final class SkillEvolutionPayloadCodec")),
                () -> assertTrue(patchEncoder.contains("final class SkillEvolutionPatchJsonEncoder")),
                () -> assertFalse(payloadCodec.contains("org.springframework")),
                () -> assertFalse(patchEncoder.contains("org.springframework")),
                () -> assertFalse(payloadCodec.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(patchEncoder.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void triggerAdaptersOwnRuntimeMapJsonAuditAndCompositionConcerns() throws IOException {
        String input = read(TRIGGER + "OpsSkillEvolutionInputAdapter.java");
        String pipeline = read(TRIGGER + "OpsSkillEvolutionPipelineAdapter.java");
        String audit = read(TRIGGER + "OpsSkillEvolutionAuditAdapter.java");
        String configuration = read(TRIGGER + "OpsSkillCatalogApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(input.contains("implements SkillEvolutionTraceInputPort, SkillEvolutionChatInputPort")),
                () -> assertTrue(input.contains("GraphEventApplicationService")),
                () -> assertFalse(input.contains("OpsGraphEventService")),
                () -> assertTrue(input.contains("OpsChatSessionService")),
                () -> assertTrue(pipeline.contains("SkillEvolutionAuthoringPort")),
                () -> assertTrue(pipeline.contains("SkillEvolutionSimilarityPort")),
                () -> assertTrue(pipeline.contains("SkillEvolutionPipelineAuditPort")),
                () -> assertFalse(pipeline.contains("SkillEvolutionPayloadCodecPort")),
                () -> assertTrue(pipeline.contains("OpsSkillAuthoringAgent")),
                () -> assertTrue(pipeline.contains("OpsSkillSimilarityService")),
                () -> assertTrue(pipeline.contains("OpsConfigAuditService")),
                () -> assertFalse(pipeline.contains("JSON.toJSONString")),
                () -> assertFalse(pipeline.contains("implements SkillEvolutionPipelinePort")),
                () -> assertFalse(pipeline.contains("OpsSkillEvolutionPipelineService")),
                () -> assertFalse(Files.exists(projectRoot().resolve(TRIGGER + "OpsSkillEvolutionPatchJsonAdapter.java"))),
                () -> assertTrue(configuration.contains("new SkillEvolutionPayloadCodec()")),
                () -> assertTrue(configuration.contains("new SkillEvolutionPatchJsonEncoder()")),
                () -> assertFalse(configuration.contains("SkillEvolutionPayloadCodecPort")),
                () -> assertFalse(configuration.contains("SkillEvolutionPatchJsonCodecPort")),
                () -> assertTrue(configuration.contains("UUID.randomUUID()")),
                () -> assertTrue(configuration.contains("Clock.systemDefaultZone()")),
                () -> assertFalse(configuration.contains("SkillEvolutionIdentityPort")),
                () -> assertFalse(configuration.contains("SkillEvolutionClockPort")),
                () -> assertTrue(audit.contains("implements SkillEvolutionAuditPort")),
                () -> assertTrue(audit.contains("OpsConfigAuditService")),
                () -> assertTrue(audit.contains("recordRuntimeEvent(")));
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
