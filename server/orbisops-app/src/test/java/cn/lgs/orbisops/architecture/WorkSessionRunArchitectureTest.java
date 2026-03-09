package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSessionRunArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/worksession/run/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/worksession/run/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcWorkSessionRunRepository.java";
    private static final String IDENTITY_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/worksession/SystemWorkSessionRunIdentityAdapter.java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsRunStateClaimManifestCheckpointAclAndRecoveryPolicy() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/WorkSessionRunPolicy.java");
        String repository = read(DOMAIN + "adapter/repository/IWorkSessionRunRepository.java");

        assertAll(
                () -> assertTrue(domain.contains("enum WorkSessionRunStatus")),
                () -> assertTrue(domain.contains("enum WorkSessionParticipantRole")),
                () -> assertTrue(domain.contains("record WorkSessionRunClaim")),
                () -> assertTrue(domain.contains("record WorkSessionRunSnapshot")),
                () -> assertTrue(domain.contains("record WorkSessionCheckpoint")),
                () -> assertTrue(domain.contains("record WorkSessionRecoveryDecision")),
                () -> assertTrue(policy.contains("prepareStart")),
                () -> assertTrue(policy.contains("mergeContextManifest")),
                () -> assertTrue(policy.contains("actorAllowed")),
                () -> assertTrue(policy.contains("recoveryDecision")),
                () -> assertTrue(policy.contains("WORK_SESSION_AGENT_VERSION_NOT_PINNED")),
                () -> assertTrue(policy.contains("APPROVED_LANDING")),
                () -> assertTrue(repository.contains("WorkSessionRunClaim claim")),
                () -> assertTrue(repository.contains("long appendCheckpoint")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("OpsAgentChatRequest")),
                () -> assertFalse(domain.contains("ai_ops_agent_run")));
    }

    @Test
    void applicationOwnsRunUseCasesWithoutTriggerOrInfrastructureLeakage() throws IOException {
        String application = readJavaTree(APPLICATION);
        String service = read(APPLICATION + "WorkSessionRunApplicationService.java");

        assertAll(
                () -> assertTrue(service.contains("IWorkSessionRunRepository")),
                () -> assertTrue(service.contains("WorkSessionRunPolicy")),
                () -> assertTrue(service.contains("public WorkSessionRunClaim begin")),
                () -> assertTrue(service.contains("public String bindContextBundle")),
                () -> assertTrue(service.contains("public void heartbeat")),
                () -> assertTrue(service.contains("public long checkpoint")),
                () -> assertTrue(service.contains("public void finish")),
                () -> assertTrue(service.contains("requestCancelForActor")),
                () -> assertTrue(service.contains("assertActorCanRead")),
                () -> assertTrue(service.contains("recoverExpiredLeases")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("OpsAgentChatRequest")),
                () -> assertFalse(application.contains("ai_ops_agent_run")));
    }

    @Test
    void infrastructureExclusivelyOwnsTransactionsSqlJsonFencingParticipantSnapshotAndWorkerIdentity() throws IOException {
        String infrastructure = read(INFRASTRUCTURE);
        String identity = read(IDENTITY_ADAPTER);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IWorkSessionRunRepository")),
                () -> assertTrue(infrastructure.contains("@Transactional(transactionManager = \"mysqlTransactionManager\")")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_run")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_run_attempt")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_run_checkpoint")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_run_participant")),
                () -> assertTrue(infrastructure.contains("LAST_INSERT_ID(next_checkpoint_seq + 1)")),
                () -> assertTrue(infrastructure.contains("FORCE INDEX (uk_run_id)")),
                () -> assertTrue(infrastructure.contains("current_attempt_id=? AND lease_token=?")),
                () -> assertTrue(infrastructure.contains("AND user_id=? AND session_id=?")),
                () -> assertTrue(infrastructure.contains("fencing_token=?")),
                () -> assertTrue(infrastructure.contains("state_version=?")),
                () -> assertTrue(infrastructure.contains("SESSION_SNAPSHOT")),
                () -> assertFalse(infrastructure.contains("TOOL_EXECUTION_REPLAY_NOT_SAFE")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertTrue(identity.contains("implements WorkSessionRunIdentityPort")),
                () -> assertTrue(identity.contains("InetAddress.getLocalHost()")),
                () -> assertTrue(identity.contains("private final String workerId")),
                () -> assertFalse(identity.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void triggerUsesTypedAdapterAndLegacyServiceIsDeleted() throws IOException {
        String production = readJavaTree(TRIGGER);
        String adapter = read(TRIGGER + "ops/runtime/OpsWorkSessionRunAdapter.java");
        String mapper = read(TRIGGER + "ops/runtime/OpsWorkSessionRunMapper.java");
        String config = read(TRIGGER + "application/worksession/WorkSessionRunApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(production.contains("OpsWorkSessionRunAdapter")),
                () -> assertTrue(adapter.contains("WorkSessionRunApplicationService")),
                () -> assertTrue(mapper.contains("WorkSessionRunStartCommand")),
                () -> assertTrue(mapper.contains("WorkSessionRunClaim")),
                () -> assertTrue(config.contains("WorkSessionRunApplicationService")),
                () -> assertFalse(production.contains("OpsWorkSessionRunIdentityAdapter")),
                () -> assertFalse(production.contains("OpsWorkSessionRunService")),
                () -> assertFalse(production.contains("ai_ops_agent_run_attempt")),
                () -> assertFalse(production.contains("ai_ops_agent_run_checkpoint")),
                () -> assertFalse(production.contains("ai_ops_agent_run_participant")),
                () -> assertFalse(production.contains("LAST_INSERT_ID(next_checkpoint_seq + 1)")));
    }

    private String readJavaTree(String relativeRoot) throws IOException {
        Path root = projectRoot().resolve(relativeRoot);
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
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
