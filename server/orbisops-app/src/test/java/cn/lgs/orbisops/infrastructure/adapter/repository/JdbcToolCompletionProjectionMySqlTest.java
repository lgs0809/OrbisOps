package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditDraft;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionCheckpoint;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class JdbcToolCompletionProjectionMySqlTest {

    private static final Instant NOW = Instant.parse("2026-08-03T07:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("orbisops_test")
            .withUsername("agent")
            .withPassword("agent");

    private JdbcTemplate jdbc;
    private JdbcToolExecutionIdempotencyAdapter first;
    private JdbcToolExecutionIdempotencyAdapter second;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        first = adapter(jdbc, true);
        first.initialize();
        second = adapter(jdbc, false);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.update("DELETE FROM ai_ops_tool_execution_completion_projection");
        insertProjection();
    }

    @Test
    void twoAdaptersMustClaimOnceThenAllowExpiredLeaseTakeoverWithNewFence() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<ToolExecutionIdempotencyPort.ProjectionDelivery> left;
        List<ToolExecutionIdempotencyPort.ProjectionDelivery> right;
        try {
            Future<List<ToolExecutionIdempotencyPort.ProjectionDelivery>> firstClaim =
                    executor.submit(() -> claim(first, "worker-a", start));
            Future<List<ToolExecutionIdempotencyPort.ProjectionDelivery>> secondClaim =
                    executor.submit(() -> claim(second, "worker-b", start));
            start.countDown();
            left = firstClaim.get();
            right = secondClaim.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, left.size() + right.size());
        ToolExecutionIdempotencyPort.ProjectionDelivery original =
                left.isEmpty() ? right.get(0) : left.get(0);
        assertEquals(1L, original.fencingToken());
        assertTrue(original.claimed());

        JdbcToolExecutionIdempotencyAdapter other =
                "worker-a".equals(original.ownerToken()) ? second : first;
        assertTrue(other.claimProjection(
                new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                        projection(), "worker-c", NOW.plusSeconds(10), NOW.plusSeconds(40)))
                .isEmpty());

        ToolExecutionIdempotencyPort.ProjectionDelivery takeover =
                other.claimProjection(
                        new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                                projection(), "worker-c", NOW.plusSeconds(31), NOW.plusSeconds(61)))
                        .orElseThrow();
        assertEquals(2L, takeover.fencingToken());

        IllegalStateException stale = assertThrows(
                IllegalStateException.class,
                () -> first.projectionSucceeded(
                        new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                                "projection-1",
                                ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                                original.ownerToken(),
                                original.fencingToken(),
                                NOW.plusSeconds(32))));
        assertEquals("TOOL_COMPLETION_PROJECTION_FENCED", stale.getMessage());

        second.projectionSucceeded(
                new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                        "projection-1",
                        ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                        takeover.ownerToken(),
                        takeover.fencingToken(),
                        NOW.plusSeconds(32)));
        second.releaseProjection(
                new ToolExecutionIdempotencyPort.ProjectionReleaseCommand(
                        "projection-1",
                        takeover.ownerToken(),
                        takeover.fencingToken(),
                        NOW.plusSeconds(32)));

        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT checkpoint_status, audit_status, owner_token,
                       fencing_token, lease_expires_at
                  FROM ai_ops_tool_execution_completion_projection
                 WHERE projection_id='projection-1'
                """);
        assertEquals("SUCCEEDED", stored.get("checkpoint_status"));
        assertEquals("PENDING", stored.get("audit_status"));
        assertEquals("", stored.get("owner_token"));
        assertEquals(2L, ((Number) stored.get("fencing_token")).longValue());
        assertEquals(null, stored.get("lease_expires_at"));
    }

    @Test
    void checkpointRedeliveryMustReuseExistingSequenceInRealMySql() {
        jdbc.execute("DROP TABLE IF EXISTS ai_ops_agent_run_checkpoint");
        jdbc.execute("DROP TABLE IF EXISTS ai_ops_agent_run");
        jdbc.execute("""
                CREATE TABLE ai_ops_agent_run (
                  run_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  current_attempt_id VARCHAR(80) NOT NULL,
                  lease_token VARCHAR(80) NOT NULL,
                  lease_expires_at DATETIME(6) NOT NULL,
                  fencing_token BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  cancel_requested TINYINT NOT NULL,
                  next_checkpoint_seq BIGINT NOT NULL DEFAULT 0,
                  updated_at DATETIME(3) NOT NULL,
                  PRIMARY KEY (run_id),
                  UNIQUE KEY uk_run_id (run_id)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE ai_ops_agent_run_checkpoint (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  run_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  attempt_id VARCHAR(80) NOT NULL,
                  checkpoint_seq BIGINT NOT NULL,
                  checkpoint_type VARCHAR(64) NOT NULL,
                  checkpoint_json MEDIUMTEXT NOT NULL,
                  checkpoint_hash VARCHAR(64) NOT NULL,
                  delivery_key VARCHAR(160) NULL,
                  created_at DATETIME(3) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_run_checkpoint_seq (run_id, checkpoint_seq),
                  UNIQUE KEY uk_checkpoint_delivery (run_id, project_id, delivery_key)
                ) ENGINE=InnoDB
                """);
        jdbc.update("""
                INSERT INTO ai_ops_agent_run
                  (run_id,project_id,current_attempt_id,lease_token,fencing_token,status,
                   cancel_requested,next_checkpoint_seq,lease_expires_at,updated_at)
                VALUES ('run-1','project-1','attempt-1','lease-1',1,'RUNNING',0,0,CURRENT_TIMESTAMP(6) + INTERVAL 1 MINUTE,?)
                """, Timestamp.from(NOW));
        JdbcWorkSessionRunRepository repository = new JdbcWorkSessionRunRepository(jdbc);
        WorkSessionRunClaim claim = new WorkSessionRunClaim(
                "run-1", "project-1", "attempt-1", "lease-1", 1L, 1L, "manifest-hash");
        WorkSessionCheckpoint checkpoint = new WorkSessionCheckpoint(
                "TOOL_EXECUTION_COMPLETED", Map.of("projectionId", "projection-1"),
                "checkpoint-hash", NOW);

        long firstSequence = transactions.execute(status -> repository.appendCheckpoint(
                claim, checkpoint, "tool-completion-checkpoint:projection-1"));
        long secondSequence = transactions.execute(status -> repository.appendCheckpoint(
                claim, checkpoint, "tool-completion-checkpoint:projection-1"));

        assertEquals(firstSequence, secondSequence);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_ops_agent_run_checkpoint", Integer.class));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT next_checkpoint_seq FROM ai_ops_agent_run WHERE run_id='run-1'", Long.class));
    }

    @Test
    void duplicateAuditIdentityMustReturnExistingRowInRealMySql() {
        jdbc.execute("DROP TABLE IF EXISTS ai_ops_config_audit");
        jdbc.execute("""
                CREATE TABLE ai_ops_config_audit (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  audit_id VARCHAR(64) NOT NULL,
                  project_id VARCHAR(80), agent_id VARCHAR(80), module_name VARCHAR(80) NOT NULL,
                  action_name VARCHAR(80) NOT NULL, target_type VARCHAR(80), target_id VARCHAR(160),
                  risk_level VARCHAR(32) NOT NULL, result_status VARCHAR(32) NOT NULL,
                  operator_id VARCHAR(80), operator_name VARCHAR(80), operator_role VARCHAR(40),
                  client_ip VARCHAR(80), trace_id VARCHAR(160), before_json MEDIUMTEXT,
                  after_json MEDIUMTEXT, create_time DATETIME(3) NOT NULL,
                  PRIMARY KEY (id), UNIQUE KEY uk_audit_id (audit_id)
                ) ENGINE=InnoDB
                """);
        JdbcConfigAuditRepository repository = new JdbcConfigAuditRepository(
                provider(jdbc), false, false, "test");
        ConfigAuditDraft draft = new ConfigAuditDraft(
                "audit-stable-1", "project-1", "agent-1", "tool-execution", "completed",
                "tool", "database/query", "LOW", "SUCCESS", "alice", "alice", "ADMIN",
                "127.0.0.1", "trace-1", "{}", "{}", LocalDateTime.now());

        String firstId = repository.append(draft).auditId();
        String secondId = repository.append(draft).auditId();

        assertEquals(firstId, secondId);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_ops_config_audit", Integer.class));
    }

    private List<ToolExecutionIdempotencyPort.ProjectionDelivery> claim(
            JdbcToolExecutionIdempotencyAdapter adapter,
            String owner,
            CountDownLatch start) {
        await(start);
        return transactions.execute(status -> adapter.claimProjections(
                new ToolExecutionIdempotencyPort.ProjectionBatchClaimCommand(
                        owner, NOW, NOW.plusSeconds(30), 10)));
    }

    private void insertProjection() {
        jdbc.update("""
                INSERT INTO ai_ops_tool_execution_completion_projection (
                  projection_id, idempotency_key, projection_json,
                  checkpoint_status, audit_status, owner_token, fencing_token,
                  lease_expires_at, attempts, next_attempt_at, last_error,
                  created_at, updated_at
                ) VALUES (?,?,?,?,?,'',0,NULL,0,?,'',?,?)
                """,
                "projection-1",
                "idem-1",
                projectionJson(),
                "PENDING",
                "PENDING",
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                Timestamp.from(NOW));
    }

    private JdbcToolExecutionIdempotencyAdapter adapter(
            JdbcTemplate template,
            boolean autoInit) {
        return new JdbcToolExecutionIdempotencyAdapter(provider(template), autoInit);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private ToolExecutionIdempotencyPort.CompletionProjection projection() {
        return new ToolExecutionIdempotencyPort.CompletionProjection(
                "projection-1",
                "project-1",
                "alice",
                "alice",
                "database",
                "query",
                "PRE_APPROVAL_WORKFLOW",
                "session-1",
                "run-1",
                Map.of("workflowNodeId", "node-1"),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("resultId", "result-1"),
                new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        "project-1", "allowed", "database/query", Map.of()));
    }

    private String projectionJson() {
        ToolExecutionIdempotencyPort.CompletionProjection projection = projection();
        return JSON.toJSONString(Map.ofEntries(
                Map.entry("projectionId", projection.projectionId()),
                Map.entry("projectId", projection.projectId()),
                Map.entry("userId", projection.userId()),
                Map.entry("actor", projection.actor()),
                Map.entry("toolsetId", projection.toolsetId()),
                Map.entry("toolName", projection.toolName()),
                Map.entry("executionScope", projection.executionScope()),
                Map.entry("sessionId", projection.sessionId()),
                Map.entry("runId", projection.runId()),
                Map.entry("requestContext", projection.requestContext()),
                Map.entry("checkpointType", projection.checkpointType()),
                Map.entry("checkpointPayload", projection.checkpointPayload()),
                Map.entry("audit", Map.of(
                        "projectId", projection.auditEvent().projectId(),
                        "action", projection.auditEvent().action(),
                        "targetId", projection.auditEvent().targetId(),
                        "payload", projection.auditEvent().payload()))));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
