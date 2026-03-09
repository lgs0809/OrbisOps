package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedWorkflowAndSkillToolExecutionArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/runtime/workflow/";

    @Test
    void workflowAndSkillReplayMustShareUnifiedToolExecutionService() throws IOException {
        String workflow = read(TRIGGER + "runtime/OpsWorkflowBoundToolExecutionAdapter.java");
        String replay = read(TRIGGER + "skill/OpsSkillBehaviorToolExecutionAdapter.java");

        assertAll(
                () -> assertTrue(workflow.contains("ToolExecutionApplicationService")),
                () -> assertTrue(replay.contains("ToolExecutionApplicationService")),
                () -> assertTrue(workflow.contains("ToolExecutionScope.PRE_APPROVAL_WORKFLOW")),
                () -> assertTrue(replay.contains("ToolExecutionScope.PRE_APPROVAL_WORKFLOW")),
                () -> assertTrue(workflow.contains("response.recorded().resultId()")),
                () -> assertTrue(workflow.contains("response.recorded().evidenceId()")),
                () -> assertTrue(workflow.contains("response.recorded().outputHash()")),
                () -> assertTrue(replay.contains("response.recorded().resultId()")),
                () -> assertTrue(replay.contains("response.recorded().outputHash()")),
                () -> assertFalse(workflow.contains("APPROVED_LANDING")),
                () -> assertFalse(replay.contains("APPROVED_LANDING")),
                () -> assertFalse(workflow.contains("ChangePackageLanding")),
                () -> assertFalse(replay.contains("ChangePackageLanding")));
    }

    @Test
    void durableWorkflowMustDeriveToolIdentityFromCheckpointState() throws IOException {
        String service = read(APPLICATION + "WorkflowToolNodeApplicationService.java");
        String invocation = read(APPLICATION + "WorkflowBoundToolInvocation.java");

        assertAll(
                () -> assertTrue(service.contains("DurableWorkflowRunState")),
                () -> assertTrue(service.contains("DurableWorkflowNodeStatus.RUNNING")),
                () -> assertTrue(service.contains("runtimePolicy.toolIdempotencyKey")),
                () -> assertTrue(service.contains("node.attempt()")),
                () -> assertTrue(invocation.contains(
                        "runId + \":\" + nodeId + \":\" + attempt + \":\" + toolCallIndex")),
                () -> assertTrue(invocation.contains(
                        "WORKFLOW_TOOL_WRITE_REQUIRES_CHANGE_PACKAGE_PROPOSAL")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("ToolCallback")));
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
