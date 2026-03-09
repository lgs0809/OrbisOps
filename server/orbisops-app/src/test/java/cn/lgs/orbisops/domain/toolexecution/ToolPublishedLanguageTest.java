package cn.lgs.orbisops.domain.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolErrorCategory;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocation;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocationContext;
import cn.lgs.orbisops.domain.toolset.model.BoundToolReference;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolReference;
import cn.lgs.orbisops.domain.toolset.model.ToolSchema;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPublishedLanguageTest {

    @Test
    void invocationRoundTripPreservesReferenceContextArgumentsAndTimeout() {
        ToolInvocation invocation = new ToolInvocation(
                new ToolReference("db.mysql.readonly", "mysql_query_readonly"),
                Map.of("sql", "select 1"),
                new ToolInvocationContext(
                        "project-1", "alice", "alice", "session-1", "run-1",
                        ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                        Map.of("traceId", "trace-1"), Map.of()),
                Duration.ofSeconds(45));

        ToolExecutionRequest request = ToolExecutionRequest.from(invocation);
        ToolInvocation restored = request.invocation();

        assertEquals(invocation.reference(), restored.reference());
        assertEquals(invocation.arguments(), restored.arguments());
        assertEquals(invocation.context().projectId(), restored.context().projectId());
        assertEquals(invocation.context().attributes().get("traceId"),
                restored.context().attributes().get("traceId"));
        assertEquals(Duration.ofSeconds(45), restored.timeout());
    }

    @Test
    void boundReferenceContainsProviderSemanticsAndSchemaWithoutLiveRuntimeObjects() {
        BoundToolReference bound = new BoundToolReference(
                "project-1",
                new ToolReference("skill.catalog", "skill_load"),
                ToolProviderDescriptor.builtIn("SKILL"),
                ToolSemantics.readOnlyTool(),
                ToolSchema.inputOnly("{\"type\":\"object\"}"));

        assertEquals("project-1:skill.catalog/skill_load", bound.canonicalId());
        assertEquals("SKILL", bound.provider().adapterType());
        assertTrue(bound.semantics().retrySafe());
        assertTrue(bound.schema().inputSchemaJson().contains("object"));
    }

    @Test
    void targetCompatibilityProjectionProducesBoundReference() {
        ToolExecutionTarget target = new ToolExecutionTarget(
                "db.mysql.change", "mysql_execute_change", "MCP", "HIGH",
                false, false, true, true, true);

        BoundToolReference bound = target.bind("project-1");

        assertEquals("db.mysql.change/mysql_execute_change", bound.reference().canonicalId());
        assertEquals("MCP", bound.provider().adapterType());
        assertTrue(bound.semantics().writesTargetResource());
    }

    @Test
    void resultFactoriesKeepAllowedAndBlockedSemanticsDistinct() {
        BoundToolReference tool = new BoundToolReference(
                "project-1",
                new ToolReference("toolset", "tool"),
                ToolProviderDescriptor.builtIn("NATIVE"),
                ToolSemantics.readOnlyTool(),
                ToolSchema.empty());
        ToolExecutionRecordedResult recorded = recorded();

        ToolExecutionResult allowed = ToolExecutionResult.allowed(tool, Map.of("value", 1), recorded);
        ToolExecutionResult blocked = ToolExecutionResult.blocked(
                tool, "POLICY_BLOCKED", "denied", Map.of(), recorded);

        assertTrue(allowed.success());
        assertEquals(ToolErrorCategory.NONE, allowed.errorCategory());
        assertEquals(ToolErrorCategory.POLICY_BLOCKED, blocked.errorCategory());
        assertEquals("BLOCKED", blocked.status());
    }

    @Test
    void timeoutMustBePositiveAndBounded() {
        ToolInvocationContext context = new ToolInvocationContext(
                "project-1", "alice", "alice", "", "", null, Map.of(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> new ToolInvocation(
                new ToolReference("toolset", "tool"), Map.of(), context, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocation(
                new ToolReference("toolset", "tool"), Map.of(), context, Duration.ofHours(1)));
    }

    private ToolExecutionRecordedResult recorded() {
        return new ToolExecutionRecordedResult(
                "result-1", "evidence-1", "preview", "a".repeat(64), false,
                "db:result-1", "b".repeat(64), 10L);
    }
}
