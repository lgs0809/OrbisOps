package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionJobDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/service/SkillEvolutionJobPolicy.java";
    private static final String JOB = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionJobSnapshot.java";
    private static final String PATCH = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionPatchSnapshot.java";
    private static final String RETRY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionRetryTransition.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionJobApplicationService.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsSkillEvolutionService.java";

    @Test
    void domainOwnsTypedJobPatchIdentityBoundsAndTransitions() throws IOException {
        String policy = read(POLICY);
        String job = read(JOB);
        String patch = read(PATCH);
        String retry = read(RETRY);

        assertAll(
                () -> assertTrue(job.contains("record SkillEvolutionJobSnapshot")),
                () -> assertTrue(job.contains("SkillEvolutionJobStatus status")),
                () -> assertTrue(patch.contains("record SkillEvolutionPatchSnapshot")),
                () -> assertTrue(retry.contains("record SkillEvolutionRetryTransition")),
                () -> assertTrue(policy.contains("\"skill-evo-\" + md5(run)")),
                () -> assertTrue(policy.contains("mergedTriggerReason(")),
                () -> assertTrue(policy.contains("claimedAttempts(")),
                () -> assertTrue(policy.contains("failureTransition(")),
                () -> assertTrue(policy.contains("terminalStatus(")),
                () -> assertTrue(policy.contains("patchStatus(")),
                () -> assertTrue(policy.contains("candidateDecision(")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationDelegatesJobRulesAndFacadeDoesNotRetainHistoricalHelpers() throws IOException {
        String application = read(APPLICATION);
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(application.contains("jobPolicy.jobId(")),
                () -> assertTrue(application.contains("jobPolicy.triggerReason(")),
                () -> assertTrue(application.contains("jobPolicy.batchSize(")),
                () -> assertTrue(application.contains("jobPolicy.listLimit(")),
                () -> assertTrue(application.contains("jobPolicy.maxAttempts(")),
                () -> assertTrue(application.contains("jobPolicy.candidateDecision(")),
                () -> assertTrue(application.contains("jobPolicy.terminalStatus(")),
                () -> assertTrue(application.contains("jobPolicy.failureTransition(")),
                () -> assertTrue(application.contains("jobPolicy.patchStatus(")),
                () -> assertFalse(service.contains("SkillEvolutionJobPolicy")),
                () -> assertFalse(service.contains("DigestUtils")),
                () -> assertFalse(service.contains("StandardCharsets")),
                () -> assertFalse(service.contains("stableHash(")),
                () -> assertFalse(service.contains("terminalJobStatus(")),
                () -> assertFalse(service.contains("Math.max(1, Math.min(batchSize, 20))")),
                () -> assertFalse(service.contains("Math.max(1, Math.min(limit, 500))")));
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
