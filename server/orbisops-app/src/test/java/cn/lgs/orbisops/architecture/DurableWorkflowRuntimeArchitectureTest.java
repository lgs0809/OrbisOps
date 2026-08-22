package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableWorkflowRuntimeArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/workflow/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/runtime/workflow/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void durableStateMachineMustConsumeOnlyBoundPlanAndRemainFrameworkFree() throws IOException {
        String policy = read(DOMAIN + "service/DurableWorkflowRuntimePolicy.java");
        String state = read(DOMAIN + "model/DurableWorkflowRunState.java");
        String service = read(APPLICATION + "DurableWorkflowRuntimeApplicationService.java");

        assertAll(
                () -> assertTrue(policy.contains("BoundWorkflowExecutionPlan")),
                () -> assertTrue(policy.contains("DURABLE_WORKFLOW_RECOVERY_PLAN_MISMATCH")),
                () -> assertTrue(policy.contains("runId() + \":\" + nodeId + \":\" + node.attempt()")),
                () -> assertTrue(state.contains("Map<String, DurableWorkflowNodeState>")),
                () -> assertTrue(state.contains("List<DurableWorkflowRouteDecision>")),
                () -> assertTrue(state.contains("DurableWorkflowWaitState")),
                () -> assertFalse(policy.contains("ChatModel")),
                () -> assertFalse(policy.contains("ToolCallback")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("StateGraph")));
    }

    @Test
    void typedLifecycleMustDefaultToDurableExecutionsInsteadOfUniversalStateGraphShadowing() throws IOException {
        String settings = read(TRIGGER + "OpsTypedWorkflowSettings.java");
        String coordinator = read(TRIGGER + "OpsTypedWorkflowExecutionCoordinator.java");

        assertAll(
                () -> assertTrue(settings.contains("orbisops.workflow.typed.scope:DURABLE_ONLY")),
                () -> assertTrue(settings.contains("OpsWorkSessionClaimMetadata.ATTEMPT_ID")),
                () -> assertTrue(settings.contains("resumedFromAttemptId")),
                () -> assertTrue(settings.contains("durableWorkflow")),
                () -> assertTrue(coordinator.contains("NOT_DURABLE_EXECUTION")),
                () -> assertTrue(coordinator.contains("StateGraph 独立执行")));
    }

    @Test
    void checkpointingMustReuseWorkSessionLeaseAndLatestSequence() throws IOException {
        String coordinator = read(TRIGGER + "OpsDurableWorkflowRuntimeCoordinator.java");
        String workSessionAdapter = read(TRIGGER + "OpsWorkSessionRunAdapter.java");
        String repository = read(INFRASTRUCTURE + "JdbcWorkSessionRunRepository.java");
        String migration = read("db/migrations/sql/ops-completion-projection-downstream-idempotency.sql");

        assertAll(
                () -> assertTrue(coordinator.contains("OpsWorkSessionRunAdapter")),
                () -> assertTrue(coordinator.contains("CHECKPOINT_PREFIX + transition.checkpoint().type().name()")),
                () -> assertTrue(workSessionAdapter.contains("application.checkpoint")),
                () -> assertTrue(workSessionAdapter.contains("tool-completion-checkpoint:")),
                () -> assertTrue(repository.contains("ORDER BY checkpoint_seq DESC LIMIT 1")),
                () -> assertTrue(repository.contains("current_attempt_id=? AND lease_token=?")),
                () -> assertTrue(repository.contains("INSERT IGNORE INTO ai_ops_agent_run_checkpoint")),
                () -> assertTrue(repository.contains("delivery_key=?")),
                () -> assertTrue(migration.contains("uk_checkpoint_delivery")),
                () -> assertFalse(coordinator.contains("JdbcTemplate")),
                () -> assertFalse(coordinator.contains("new Thread")),
                () -> assertFalse(coordinator.contains("ExecutorService")),
                () -> assertFalse(coordinator.contains("synchronized")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
