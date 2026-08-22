package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionJobFacadeArchitectureTest {

    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionService.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionJobMapper.java";

    @Test
    void legacyServiceDelegatesTypedSettingsTriggerWorkerAndSchedulingBoundaries() throws IOException {
        String service = read(SERVICE);
        String mapper = read(MAPPER);
        String settings = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionSettings.java");
        String trigger = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionTriggerCoordinator.java");
        String worker = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionWorkerCoordinator.java");
        String configuration = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionConfiguration.java");
        String workerJob = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionWorkerJob.java");

        assertAll(
                () -> assertTrue(service.contains("SkillEvolutionJobApplicationService")),
                () -> assertTrue(service.contains("OpsSkillEvolutionJobMapper")),
                () -> assertTrue(service.contains("OpsSkillEvolutionTriggerCoordinator triggerCoordinator")),
                () -> assertTrue(service.contains("OpsSkillEvolutionWorkerCoordinator workerCoordinator")),
                () -> assertTrue(service.contains("legacyConstructorDefaults()")),
                () -> assertTrue(service.contains("triggerCoordinator.enqueue(")),
                () -> assertTrue(service.contains("workerCoordinator.runBatch()")),
                () -> assertTrue(service.contains("applicationService.listJobs(")),
                () -> assertTrue(service.contains("applicationService.getJob(")),
                () -> assertTrue(service.contains("applicationService.listPatches(")),
                () -> assertFalse(service.contains("@Scheduled")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("triggerEnabled")),
                () -> assertFalse(service.contains("workerEnabled")),
                () -> assertTrue(service.lines().count() <= 110),
                () -> assertTrue(settings.contains("public record OpsSkillEvolutionSettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(trigger.contains("TRIGGER_DISABLED")),
                () -> assertTrue(trigger.contains("applicationService.enqueue(")),
                () -> assertTrue(worker.contains("WORKER_DISABLED")),
                () -> assertTrue(worker.contains("applicationService.runBatch(")),
                () -> assertFalse(trigger.contains("@Service")),
                () -> assertFalse(worker.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.skill-evolver.trigger.enabled")),
                () -> assertTrue(configuration.contains("orbisops.skill-evolver.worker.max-attempts")),
                () -> assertTrue(workerJob.contains("@Scheduled")),
                () -> assertTrue(workerJob.contains("evolutionService.scheduledRun()")),
                () -> assertTrue(mapper.contains("Legacy Map ACL")),
                () -> assertTrue(mapper.contains("JSON.parseObject")),
                () -> assertFalse(service.contains("ISkillEvolutionJobRepository")),
                () -> assertFalse(service.contains("SkillEvolutionJobPolicy")),
                () -> assertFalse(service.contains("SkillEvolutionInputPolicy")),
                () -> assertFalse(service.contains("OpsGraphEventService")),
                () -> assertFalse(service.contains("OpsChatSessionService")),
                () -> assertFalse(service.contains("OpsSkillEvolutionPipelineService")),
                () -> assertFalse(service.contains("OpsConfigAuditService")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("UUID.randomUUID")),
                () -> assertFalse(service.contains("Instant.now()")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("CREATE TABLE")),
                () -> assertFalse(service.contains("ai_ops_skill_evolution_job")),
                () -> assertFalse(service.contains("processJob(")),
                () -> assertFalse(service.contains("writePatch(")),
                () -> assertFalse(service.contains("parseObject(")));
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
