package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the Phase 652 removals so retired routing and production writers cannot silently return. */
class RetiredIntentAndLandingWriterArchitectureTest {

    @Test
    void retiredIntentAndDeterministicLandingImplementationsStayPhysicallyRemoved() throws IOException {
        Path root = projectRoot();
        String trigger = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";
        String application = "orbisops-application/src/main/java/cn/lgs/orbisops/application/";
        String domain = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/";

        assertAll(
                () -> assertFalse(Files.exists(root.resolve(trigger + "ops/intent"))),
                () -> assertFalse(Files.exists(root.resolve(application + "intent"))),
                () -> assertFalse(Files.exists(root.resolve(domain + "intent"))),
                () -> assertFalse(Files.exists(root.resolve(trigger + "application/ops/OpsChatFixedActionService.java"))),
                () -> assertFalse(Files.exists(root.resolve(trigger + "ops/runtime/OpsRuntimeIntentService.java"))),
                () -> assertFalse(Files.exists(root.resolve(trigger + "ops/change/OpsChangePackageLandingRuntime.java"))),
                () -> assertFalse(Files.exists(root.resolve(trigger + "ops/change/OpsLocalLandingOperationExecutor.java"))),
                () -> assertFalse(Files.exists(root.resolve(trigger + "ops/change/OpsMcpLandingOperationExecutor.java"))));
    }

    @Test
    void currentAgentLandingAndToolExecutionBoundariesRemainAuthoritative() throws IOException {
        String landingDefinition = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/runtime/OpsPlatformLandingRuntimeDefinitionFactory.java");
        String landingCoordinator = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/runtime/OpsApprovedLandingAgentRunCoordinator.java");
        String toolExecution = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/toolexecution/ToolExecutionApplicationService.java");
        String reconciliation = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/http/admin/OpsToolExecutionReconciliationAdminController.java");
        String chat = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/ops/OpsChatApplicationService.java");
        String recoveryPort = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/LandingOperationJournalPort.java");
        String recoveryJdbc = read("orbisops-infrastructure/src/main/java/"
                + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcLandingOperationJournalAdapter.java");

        assertAll(
                () -> assertTrue(landingDefinition.contains("platform-landing-react")),
                () -> assertTrue(landingDefinition.contains("executionStyle\", \"REACT")),
                () -> assertTrue(landingCoordinator.contains("_trustedTriggerSource")),
                () -> assertTrue(landingCoordinator.contains("hasUnresolvedSideEffect")),
                () -> assertTrue(toolExecution.contains("APPROVED_LANDING")),
                () -> assertTrue(toolExecution.contains("TOOL_EXECUTION_RECONCILIATION_REQUIRED")),
                () -> assertTrue(toolExecution.contains("landing:auto:")),
                () -> assertTrue(reconciliation.contains("CONFIRMED_SUCCEEDED")),
                () -> assertTrue(reconciliation.contains("CONFIRMED_NOT_EXECUTED")),
                () -> assertFalse(chat.contains("OpsIntentRouter")),
                () -> assertFalse(chat.contains("OpsChatFixedActionService")),
                () -> assertTrue(recoveryPort.contains("claimRecovery(")),
                () -> assertTrue(recoveryPort.contains("completeRecovery(")),
                () -> assertFalse(recoveryPort.contains("claimDispatch(")),
                () -> assertFalse(recoveryPort.contains("markSucceeded(")),
                () -> assertFalse(recoveryPort.contains("rollback")),
                () -> assertTrue(recoveryJdbc.contains("ORDER BY create_time ASC, id ASC")),
                () -> assertFalse(recoveryJdbc.contains("ORDER BY update_time")),
                () -> assertFalse(recoveryJdbc.contains("claimDispatch(")),
                () -> assertFalse(recoveryJdbc.contains("ROLLING_BACK")));
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
