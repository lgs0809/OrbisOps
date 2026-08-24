package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionJobApplicationArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionJobApplicationService.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillCatalogApplicationConfiguration.java";

    @Test
    void applicationProcessManagerOwnsCompleteJobLifecycleThroughTypedPorts() throws IOException {
        String application = read(APPLICATION);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(application.contains("class SkillEvolutionJobApplicationService")),
                () -> assertTrue(application.contains("ISkillEvolutionJobRepository")),
                () -> assertTrue(application.contains("SkillEvolutionTraceInputPort")),
                () -> assertTrue(application.contains("SkillEvolutionChatInputPort")),
                () -> assertTrue(application.contains("SkillEvolutionPipelinePort")),
                () -> assertTrue(application.contains("SkillEvolutionPatchJsonEncoder")),
                () -> assertFalse(application.contains("SkillEvolutionPatchJsonCodecPort")),
                () -> assertTrue(application.contains("Supplier<String> patchIdSupplier")),
                () -> assertTrue(application.contains("SkillEvolutionAuditPort")),
                () -> assertTrue(application.contains("Clock clock")),
                () -> assertTrue(application.contains("public SkillEvolutionEnqueueResult enqueue(")),
                () -> assertTrue(application.contains("public List<SkillEvolutionJobRunResult> runBatch(")),
                () -> assertTrue(application.contains("repository.claimPending(")),
                () -> assertTrue(application.contains("inputPolicy.summarize(input)")),
                () -> assertTrue(application.contains("pipelinePort.decide(")),
                () -> assertTrue(application.contains("repository.complete(job, patch, terminalStatus)")),
                () -> assertTrue(application.contains("repository.complete(")),
                () -> assertTrue(application.contains("repository.rescheduleOrFail(")),
                () -> assertTrue(configuration.contains("skillEvolutionJobApplicationService(")),
                () -> assertTrue(configuration.contains("new SkillEvolutionPatchJsonEncoder()")),
                () -> assertFalse(configuration.contains("SkillEvolutionPatchJsonCodecPort")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("Map<")),
                () -> assertFalse(application.contains("UUID.randomUUID")),
                () -> assertFalse(application.contains("Instant.now()")),
                () -> assertFalse(application.contains("@Scheduled")));
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
