package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcToolCompletionProjectionClaimTest {

    private static final Instant NOW = Instant.parse("2026-08-03T06:00:00Z");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void singleClaimMustBindOwnerLeaseAndReturnFencingToken() throws Exception {
        Fixture fixture = fixture();
        when(fixture.jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ResultSet row = claimedRow("worker-a", 4L);
        when(fixture.jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        ToolExecutionIdempotencyPort.ProjectionDelivery delivery =
                fixture.adapter.claimProjection(
                        new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                                projection(), "worker-a", NOW, NOW.plusSeconds(30)))
                        .orElseThrow();

        assertTrue(delivery.claimed());
        assertEquals("worker-a", delivery.ownerToken());
        assertEquals(4L, delivery.fencingToken());
        verify(fixture.jdbc).update(
                argThat(sql -> sql.contains("fencing_token=fencing_token+1")
                        && sql.contains("owner_token='' OR lease_expires_at IS NULL")
                        && sql.contains("projection_id=?")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void batchClaimMustLockRowsAndSkipOtherInstances() throws Exception {
        Fixture fixture = fixture();
        when(fixture.jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ResultSet claimed = claimedRow("worker-b", 8L);
        when(fixture.jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    if (sql.contains("FOR UPDATE SKIP LOCKED")) {
                        ResultSet idRow = mock(ResultSet.class);
                        when(idRow.getString("projection_id")).thenReturn("projection-1");
                        return List.of(mapper.mapRow(idRow, 0));
                    }
                    return List.of(mapper.mapRow(claimed, 0));
                });

        List<ToolExecutionIdempotencyPort.ProjectionDelivery> deliveries =
                fixture.adapter.claimProjections(
                        new ToolExecutionIdempotencyPort.ProjectionBatchClaimCommand(
                                "worker-b", NOW, NOW.plusSeconds(30), 20));

        assertEquals(1, deliveries.size());
        assertEquals(8L, deliveries.get(0).fencingToken());
        verify(fixture.jdbc, atLeastOnce()).query(
                argThat(sql -> sql.contains("FOR UPDATE SKIP LOCKED")
                        && sql.contains("LIMIT ?")
                        && sql.contains("lease_expires_at<=?")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void staleOwnerMustNotAcknowledgeOrReleaseProjection() {
        Fixture fixture = fixture();
        when(fixture.jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        IllegalStateException acknowledge = assertThrows(
                IllegalStateException.class,
                () -> fixture.adapter.projectionSucceeded(
                        new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                                "projection-1",
                                ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                                "stale-worker",
                                3L,
                                NOW)));
        IllegalStateException release = assertThrows(
                IllegalStateException.class,
                () -> fixture.adapter.releaseProjection(
                        new ToolExecutionIdempotencyPort.ProjectionReleaseCommand(
                                "projection-1", "stale-worker", 3L, NOW)));

        assertEquals("TOOL_COMPLETION_PROJECTION_FENCED", acknowledge.getMessage());
        assertEquals("TOOL_COMPLETION_PROJECTION_RELEASE_FENCED", release.getMessage());
        verify(fixture.jdbc, atLeastOnce()).update(
                argThat(sql -> sql.contains("owner_token=?")
                        && sql.contains("fencing_token=?")),
                any(Object[].class));
    }

    private ResultSet claimedRow(String owner, long fence) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("projection_json")).thenReturn(projectionJson());
        when(row.getString("checkpoint_status")).thenReturn("PENDING");
        when(row.getString("audit_status")).thenReturn("FAILED");
        when(row.getInt("attempts")).thenReturn(2);
        when(row.getString("owner_token")).thenReturn(owner);
        when(row.getLong("fencing_token")).thenReturn(fence);
        when(row.getTimestamp("lease_expires_at")).thenReturn(Timestamp.from(NOW.plusSeconds(30)));
        return row;
    }

    private Fixture fixture() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new Fixture(jdbc, new JdbcToolExecutionIdempotencyAdapter(provider, false));
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

    private record Fixture(
            JdbcTemplate jdbc,
            JdbcToolExecutionIdempotencyAdapter adapter) {
    }
}
