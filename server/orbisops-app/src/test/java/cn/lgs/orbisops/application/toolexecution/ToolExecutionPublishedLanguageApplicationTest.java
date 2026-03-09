package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocation;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocationContext;
import cn.lgs.orbisops.domain.toolset.model.ToolReference;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionPublishedLanguageApplicationTest {

    @Test
    void invocationEntryUsesExistingPolicyDispatchRecordCheckpointAndAuditMainline() {
        AtomicLong nanos = new AtomicLong();
        ToolExecutionTarget target = new ToolExecutionTarget(
                "db.mysql.readonly", "mysql_query_readonly", "LOCAL_MYSQL", "LOW",
                true, false, false, false, false);
        ToolExecutionApplicationService service = new ToolExecutionApplicationService(
                request -> new ToolExecutionResolution(
                        target, ToolExecutionDecision.allowed("LOW", Map.of())),
                (resolved, request) -> Map.of("rows", 1),
                command -> recorded(command.durationMs()),
                (request, type, payload) -> { },
                event -> { },
                () -> "call-1",
                () -> nanos.addAndGet(1_000_000L));

        ToolExecutionResult result = service.execute(invocation());

        assertTrue(result.success());
        assertTrue(result.allowed());
        assertEquals("project-1:db.mysql.readonly/mysql_query_readonly",
                result.tool().canonicalId());
        assertEquals(1, result.payload().get("rows"));
        assertEquals("LOCAL_MYSQL", result.tool().provider().adapterType());
    }

    @Test
    void blockedInvocationReturnsTypedPolicyCategoryAndStillRecordsEvidence() {
        AtomicLong nanos = new AtomicLong();
        ToolExecutionTarget target = new ToolExecutionTarget(
                "db.mysql.change", "mysql_execute_change", "MCP", "HIGH",
                false, false, true, true, true);
        ToolExecutionApplicationService service = new ToolExecutionApplicationService(
                request -> new ToolExecutionResolution(
                        target,
                        new ToolExecutionDecision(
                                false, "BLOCKED", "APPROVAL_REQUIRED", "approval missing",
                                "HIGH", Map.of())),
                (resolved, request) -> Map.of(),
                command -> recorded(command.durationMs()),
                (request, type, payload) -> { },
                event -> { },
                () -> "call-1",
                () -> nanos.addAndGet(1_000_000L));

        ToolExecutionResult result = service.execute(new ToolInvocation(
                new ToolReference("db.mysql.change", "mysql_execute_change"),
                Map.of("sql", "update config"),
                invocation().context(),
                Duration.ofSeconds(30)));

        assertFalse(result.success());
        assertFalse(result.allowed());
        assertEquals("BLOCKED", result.status());
        assertEquals("BLOCKED", result.errorCode());
        assertEquals("HIGH", result.tool().semantics().riskLevel().name());
    }

    private ToolInvocation invocation() {
        return new ToolInvocation(
                new ToolReference("db.mysql.readonly", "mysql_query_readonly"),
                Map.of("sql", "select 1"),
                new ToolInvocationContext(
                        "project-1", "alice", "alice", "session-1", "run-1",
                        ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                        Map.of("traceId", "trace-1"), Map.of()),
                Duration.ofSeconds(30));
    }

    private ToolExecutionRecordedResult recorded(long durationMs) {
        return new ToolExecutionRecordedResult(
                "result-1", "evidence-1", "preview", "a".repeat(64), false,
                "db:result-1", "b".repeat(64), durationMs);
    }
}
