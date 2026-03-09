package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionAuthoritativeCompletionArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/toolexecution/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void authoritativeCompletionMustBeTransactionalAndProjectionBacked()
            throws IOException {
        String service = read(APPLICATION + "ToolExecutionApplicationService.java");
        String reconciliation = read(APPLICATION
                + "ToolExecutionCompletionReconciliationService.java");
        String transactionPort = read(APPLICATION + "ToolExecutionTransactionPort.java");
        String transactionAdapter = read(INFRASTRUCTURE
                + "SpringToolExecutionTransactionAdapter.java");
        String ledger = read(INFRASTRUCTURE
                + "JdbcToolExecutionIdempotencyAdapter.java");
        String configuration = read(TRIGGER
                + "application/toolexecution/OpsToolExecutionApplicationConfiguration.java");
        String job = read(TRIGGER
                + "job/OpsToolExecutionCompletionReconciliationJob.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "AuthoritativeCompletion completion = transactions.required")),
                () -> assertTrue(service.contains(
                        "complete(idempotencyKey, toolCallId, reservation, response, projection)")),
                () -> assertTrue(service.contains("completionReconciliation.project(projection)")),
                () -> assertFalse(service.contains(
                        "checkpoints.checkpoint(\n                    request,\n                    \"TOOL_EXECUTION_COMPLETED\"")),
                () -> assertTrue(reconciliation.contains("claimProjection")),
                () -> assertTrue(reconciliation.contains("claimProjections")),
                () -> assertFalse(reconciliation.contains("ledger.pendingProjections")),
                () -> assertTrue(reconciliation.contains("projectionSucceeded")),
                () -> assertTrue(reconciliation.contains("projectionFailed")),
                () -> assertTrue(reconciliation.contains("releaseProjection")),
                () -> assertTrue(transactionPort.contains("<T> T required")),
                () -> assertTrue(transactionAdapter.contains("mysqlTransactionManager")),
                () -> assertTrue(ledger.contains(
                        "@Transactional(transactionManager = \"mysqlTransactionManager\")")),
                () -> assertTrue(ledger.contains(
                        "INSERT INTO ai_ops_tool_execution_completion_projection")),
                () -> assertTrue(ledger.contains("FOR UPDATE SKIP LOCKED")),
                () -> assertTrue(ledger.contains("fencing_token=fencing_token+1")),
                () -> assertTrue(ledger.contains("owner_token=? AND fencing_token=?")),
                () -> assertTrue(ledger.contains("enqueueProjection")),
                () -> assertTrue(configuration.contains("ToolExecutionTransactionPort transactions")),
                () -> assertTrue(configuration.contains(
                        "ToolExecutionCompletionReconciliationService")),
                () -> assertTrue(job.contains("@Scheduled")),
                () -> assertTrue(job.contains("reconciliation.reconcile(batchSize)")));
    }

    @Test
    void controlledMigrationMustCreateLedgerAndCompletionProjection()
            throws IOException {
        String migration = read("db/migrations/sql/"
                + "ops-tool-execution-authoritative-completion.sql");

        assertAll(
                () -> assertTrue(migration.contains(
                        "CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_ledger")),
                () -> assertTrue(migration.contains(
                        "CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_completion_projection")),
                () -> assertTrue(migration.contains(
                        "UNIQUE KEY uk_tool_execution_idempotency")),
                () -> assertTrue(migration.contains(
                        "UNIQUE KEY uk_tool_completion_projection")),
                () -> assertTrue(migration.contains(
                        "UNIQUE KEY uk_tool_completion_idempotency")),
                () -> assertTrue(migration.contains("owner_token VARCHAR(128)")),
                () -> assertTrue(migration.contains("fencing_token BIGINT")),
                () -> assertTrue(migration.contains("lease_expires_at DATETIME(6)")),
                () -> assertTrue(migration.contains("idx_tool_completion_claim")));
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
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
