package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcToolExecutionIdempotencyAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");

    @Test
    void completeMustUpdateAuthoritativeLedgerAndEnqueueProjection() {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        adapter.complete(completeCommand());

        verify(jdbc).update(
                argThat(sql -> sql != null && sql.contains("UPDATE ai_ops_tool_execution_ledger")
                        && sql.contains("status=?")
                        && sql.contains("fencing_token=?")),
                any(Object[].class));
        verify(jdbc).update(
                argThat(sql -> sql != null && sql.contains(
                        "INSERT INTO ai_ops_tool_execution_completion_projection")
                        && sql.contains("ON DUPLICATE KEY UPDATE")),
                any(Object[].class));
    }

    @Test
    void completeMustJoinMysqlTransactionManager() throws Exception {
        Transactional annotation = JdbcToolExecutionIdempotencyAdapter.class
                .getMethod("complete", ToolExecutionIdempotencyPort.CompleteCommand.class)
                .getAnnotation(Transactional.class);

        assertNotNull(annotation);
        assertEquals("mysqlTransactionManager", annotation.transactionManager());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pendingProjectionMustRestoreFrozenRequestCheckpointAndAudit() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        ResultSet row = mock(ResultSet.class);
        when(row.getString("projection_json")).thenReturn(projectionJson());
        when(row.getString("checkpoint_status")).thenReturn("FAILED");
        when(row.getString("audit_status")).thenReturn("PENDING");
        when(row.getInt("attempts")).thenReturn(3);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        List<ToolExecutionIdempotencyPort.ProjectionDelivery> deliveries =
                adapter.pendingProjections(NOW, 500);

        assertEquals(1, deliveries.size());
        ToolExecutionIdempotencyPort.ProjectionDelivery delivery = deliveries.get(0);
        assertTrue(delivery.checkpointPending());
        assertTrue(delivery.auditPending());
        assertEquals(3, delivery.attempts());
        assertEquals("projection-1", delivery.projection().projectionId());
        assertEquals("node-1",
                delivery.projection().requestContext().get("workflowNodeId"));
        assertEquals("TOOL_EXECUTION_COMPLETED",
                delivery.projection().checkpointType());
        assertEquals("allowed", delivery.projection().auditEvent().action());
        assertEquals("database/query", delivery.projection().auditEvent().targetId());
        verify(jdbc).query(
                argThat(sql -> sql != null && sql.contains("next_attempt_at<=?")
                        && sql.contains("checkpoint_status IN (?,?)")
                        && sql.contains("audit_status IN (?,?)")
                        && sql.contains("LIMIT ?")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void manualConfirmedSuccessMustResolveOnlyReviewRequiredRowWithoutMakingItReplayableAsNewWork() {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        adapter.resolveSideEffect(new ToolExecutionIdempotencyPort.ResolveSideEffectCommand(
                "idem-1",
                "project-1",
                "landing-run-1",
                ToolExecutionIdempotencyPort.SideEffectResolution.CONFIRMED_SUCCEEDED,
                "evidence-1",
                "a".repeat(64),
                "authoritative provider receipt verified",
                "admin-1",
                NOW));

        verify(jdbc).update(
                argThat(sql -> sql != null && sql.contains("status=?")
                        && sql.contains("decision_code='RECONCILED_SUCCEEDED'")
                        && sql.contains("WHERE idempotency_key=? AND project_id=? AND run_id=? AND status=?")),
                any(Object[].class));
    }

    @Test
    void manualConfirmedNotExecutedMustReturnLedgerToRetryableState() {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        adapter.resolveSideEffect(new ToolExecutionIdempotencyPort.ResolveSideEffectCommand(
                "idem-1",
                "project-1",
                "landing-run-1",
                ToolExecutionIdempotencyPort.SideEffectResolution.CONFIRMED_NOT_EXECUTED,
                "evidence-2",
                "b".repeat(64),
                "authoritative state confirms no mutation",
                "admin-1",
                NOW));

        verify(jdbc).update(
                argThat(sql -> sql != null && sql.contains("decision_code='RECONCILED_NOT_EXECUTED'")
                        && sql.contains("error_code='MANUAL_RECONCILED_NOT_EXECUTED'")
                        && sql.contains("status=?")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void expiredSideEffectingRunningReservationMustQuarantineInsteadOfTakeover() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        ResultSet row = ledgerRow(true, 7L, NOW.minusSeconds(1));
        when(jdbc.update(argThat(sql -> sql != null && sql.contains("INSERT IGNORE INTO ai_ops_tool_execution_ledger")), any(Object[].class)))
                .thenReturn(0);
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("WHERE idempotency_key=?")), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        when(jdbc.update(argThat(sql -> sql != null && sql.contains("TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED")), any(Object[].class)))
                .thenReturn(1);
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        ToolExecutionIdempotencyPort.Reservation reservation = adapter.reserve(reserveCommand(true));

        assertEquals(ToolExecutionIdempotencyPort.Disposition.REVIEW_REQUIRED, reservation.disposition());
        assertEquals("TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED", reservation.reasonCode());
        verify(jdbc, never()).update(argThat(sql -> sql != null && sql.contains("fencing_token=fencing_token+1")), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void expiredReadOnlyRunningReservationMayUseFencedTakeover() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        ResultSet row = ledgerRow(false, 7L, NOW.minusSeconds(1));
        when(jdbc.update(argThat(sql -> sql != null && sql.contains("INSERT IGNORE INTO ai_ops_tool_execution_ledger")), any(Object[].class)))
                .thenReturn(0);
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("WHERE idempotency_key=?")), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        when(jdbc.update(argThat(sql -> sql != null && sql.contains("fencing_token=fencing_token+1")), any(Object[].class)))
                .thenReturn(1);
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        ToolExecutionIdempotencyPort.Reservation reservation = adapter.reserve(reserveCommand(false));

        assertEquals(ToolExecutionIdempotencyPort.Disposition.EXECUTE, reservation.disposition());
        assertEquals(8L, reservation.fencingToken());
    }

    @Test
    void projectionChannelUpdatesMustBeIdempotentAndPreserveSucceededState() {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcToolExecutionIdempotencyAdapter adapter = adapter(provider);

        adapter.projectionSucceeded(
                new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                        "projection-1",
                        ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                        NOW));
        adapter.projectionFailed(
                new ToolExecutionIdempotencyPort.ProjectionFailedCommand(
                        "projection-1",
                        ToolExecutionIdempotencyPort.ProjectionChannel.AUDIT,
                        "audit unavailable",
                        NOW,
                        NOW.plusSeconds(2)));

        verify(jdbc, atLeastOnce()).update(
                argThat(sql -> sql != null && sql.contains("checkpoint_status")
                        && sql.contains("checkpoint_status<>?")),
                any(Object[].class));
        verify(jdbc, atLeastOnce()).update(
                argThat(sql -> sql != null && sql.contains("audit_status")
                        && sql.contains("attempts=attempts+1")
                        && sql.contains("audit_status<>?")),
                any(Object[].class));
    }

    private JdbcToolExecutionIdempotencyAdapter adapter(
            ObjectProvider<JdbcTemplate> provider) {
        return new JdbcToolExecutionIdempotencyAdapter(provider, false);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }

    private ToolExecutionIdempotencyPort.ReserveCommand reserveCommand(boolean sideEffecting) {
        return new ToolExecutionIdempotencyPort.ReserveCommand(
                "idem-reserve",
                "project-1",
                "landing-run-1",
                "node-1",
                0,
                0,
                "a".repeat(64),
                "b".repeat(64),
                sideEffecting,
                Map.of("mcpServerId", "service-control", "actor", "ops-agent"),
                "owner-new",
                NOW,
                NOW.plusSeconds(30));
    }

    private ResultSet ledgerRow(boolean sideEffecting, long fencingToken, Instant leaseExpiresAt) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("idempotency_key")).thenReturn("idem-reserve");
        when(row.getString("input_hash")).thenReturn("a".repeat(64));
        when(row.getString("target_hash")).thenReturn("b".repeat(64));
        when(row.getBoolean("side_effecting")).thenReturn(sideEffecting);
        when(row.getString("reconciliation_json")).thenReturn("{\"mcpServerId\":\"service-control\"}");
        when(row.getString("status")).thenReturn("RUNNING");
        when(row.getString("owner_token")).thenReturn("owner-old");
        when(row.getLong("fencing_token")).thenReturn(fencingToken);
        when(row.getTimestamp("lease_expires_at")).thenReturn(Timestamp.from(leaseExpiresAt));
        when(row.getBoolean("allowed")).thenReturn(false);
        when(row.getString("decision_code")).thenReturn("");
        when(row.getString("result_id")).thenReturn("");
        when(row.getString("evidence_id")).thenReturn("");
        when(row.getString("preview_text")).thenReturn("");
        when(row.getString("output_hash")).thenReturn("");
        when(row.getBoolean("truncated")).thenReturn(false);
        when(row.getString("full_output_ref")).thenReturn("");
        when(row.getString("recorded_input_hash")).thenReturn("");
        when(row.getLong("duration_ms")).thenReturn(0L);
        when(row.getString("payload_json")).thenReturn("");
        when(row.getString("error_code")).thenReturn("");
        when(row.getString("error_message")).thenReturn("");
        return row;
    }

    private ToolExecutionIdempotencyPort.CompleteCommand completeCommand() {
        return new ToolExecutionIdempotencyPort.CompleteCommand(
                "idem-1",
                "owner-1",
                7L,
                true,
                "ALLOWED",
                recorded(),
                Map.of("rows", 3),
                projection(),
                NOW);
    }

    private ToolExecutionRecordedResult recorded() {
        return new ToolExecutionRecordedResult(
                "result-1",
                "evidence-1",
                "preview",
                "a".repeat(64),
                false,
                "db:result-1",
                "b".repeat(64),
                20L);
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
                Map.of(
                        "idempotencyKey", "idem-1",
                        "workflowNodeId", "node-1",
                        "workflowAttempt", 1,
                        "metadata", Map.of("_workSessionAttemptId", "attempt-1")),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("projectionId", "projection-1", "resultId", "result-1"),
                new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        "project-1",
                        "allowed",
                        "database/query",
                        Map.of("projectionId", "projection-1")));
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
}
