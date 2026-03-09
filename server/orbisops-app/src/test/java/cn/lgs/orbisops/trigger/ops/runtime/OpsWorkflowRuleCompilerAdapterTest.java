package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluationContext;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWorkflowRuleCompilerAdapterTest {

    private final OpsWorkflowRuleCompilerAdapter compiler =
            new OpsWorkflowRuleCompilerAdapter();
    private final WorkflowRuleEvaluator evaluator = new WorkflowRuleEvaluator();

    @Test
    void strictJsonAstMustCompileAndEvaluate() {
        WorkflowRule rule = compiler.parseExpression("""
                {
                  "op": "ALL",
                  "rules": [
                    {"op":"GTE","field":"runtime.round","value":2},
                    {"op":"IN","field":"approval.status","values":["APPROVED","AUTO"]},
                    {"op":"NOT","rule":{"op":"EXISTS","field":"error.message"}}
                  ]
                }
                """);
        WorkflowRuleEvaluationContext context = new WorkflowRuleEvaluationContext(Map.of(
                "runtime", Map.of("round", 2),
                "approval", Map.of("status", "APPROVED"),
                "error", Map.of()));

        assertTrue(evaluator.evaluate(rule, context));
    }

    @Test
    void changedAndNullRulesMustRemainTyped() {
        WorkflowRule changed = compiler.parseExpression(
                "{\"op\":\"CHANGED\",\"field\":\"runtime.status\"}");
        WorkflowRule equalsNull = compiler.parseExpression(
                "{\"op\":\"EQ\",\"field\":\"runtime.reason\",\"value\":null}");
        java.util.LinkedHashMap<String, Object> runtime = new java.util.LinkedHashMap<>();
        runtime.put("status", "FAILED");
        runtime.put("reason", null);
        WorkflowRuleEvaluationContext context = new WorkflowRuleEvaluationContext(
                Map.of("runtime", runtime),
                Map.of("runtime", Map.of("status", "RUNNING")));

        assertTrue(evaluator.evaluate(changed, context));
        assertTrue(evaluator.evaluate(equalsNull, context));
    }

    @Test
    void boundedLegacyExpressionsMustMigrateWithoutSpel() {
        assertTrue(evaluate("runtime.round >= 2", Map.of("runtime", Map.of("round", 2))));
        assertTrue(evaluate("#decision == 'worker'", Map.of(
                "nodeOutput", Map.of("output", "worker"))));
        assertTrue(evaluate("contains:timeout", Map.of(
                "nodeOutput", Map.of("output", "database timeout"))));
        assertTrue(evaluate("plan.tasks not empty", Map.of(
                "nodeOutput", Map.of("plan", Map.of("tasks", List.of("one"))))));
        assertTrue(evaluate("evidence_sufficient || round_limit", Map.of(
                "runtime", Map.of("evidence_sufficient", true))));
        assertTrue(evaluate("evidence_sufficient || round_limit", Map.of(
                "runtime", Map.of("round_limit", true))));
        assertFalse(evaluate("evidence_sufficient || round_limit", Map.of(
                "runtime", Map.of())));
    }

    @Test
    void dangerousDynamicLanguagesAndPathsMustBeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression("T(java.lang.Runtime).getRuntime().exec('id')"));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression("#state['round'] >= 2"));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression("runtime.items[0] == 'x'"));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression("system.env.PATH == 'x'"));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression("runtime.getClass() == 'x'"));
    }

    @Test
    void jsonUnknownOperatorsFieldsAndComplexLiteralsMustFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression(
                        "{\"op\":\"EXEC\",\"field\":\"runtime.x\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression(
                        "{\"op\":\"EQ\",\"field\":\"runtime.x\",\"value\":1,\"script\":\"x\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression(
                        "{\"op\":\"EQ\",\"field\":\"runtime.x\",\"value\":{\"a\":1}}"));
    }

    @Test
    void routeModesMustCompileToSafeRules() {
        WorkflowRule routeMatch = compiler.compile(
                AgentWorkflowRouteMode.ROUTE_MATCH, "prometheus");
        WorkflowRule review = compiler.compile(
                AgentWorkflowRouteMode.REVIEW_DECISION, "needs:rag");
        WorkflowRule error = compiler.compile(
                AgentWorkflowRouteMode.ERROR, "timeout");

        assertTrue(evaluator.evaluate(routeMatch, new WorkflowRuleEvaluationContext(Map.of(
                "runtime", Map.of("selectedRoutes", List.of("prometheus"))))));
        assertTrue(evaluator.evaluate(review, new WorkflowRuleEvaluationContext(Map.of(
                "approval", Map.of("decision", "needs:rag"),
                "nodeOutput", Map.of("output", "")))));
        assertTrue(evaluator.evaluate(error, new WorkflowRuleEvaluationContext(Map.of(
                "error", Map.of("message", "timeout while connecting")))));
    }

    @Test
    void malformedJsonAndExcessiveDepthMustFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression("{not-json}"));
        String nested = "{\"op\":\"EQ\",\"field\":\"runtime.x\",\"value\":true}";
        for (int index = 0; index < 13; index++) {
            nested = "{\"op\":\"NOT\",\"rule\":" + nested + "}";
        }
        String excessive = nested;
        assertThrows(IllegalArgumentException.class,
                () -> compiler.parseExpression(excessive));
    }

    private boolean evaluate(String expression, Map<String, Object> roots) {
        return evaluator.evaluate(
                compiler.parseExpression(expression),
                new WorkflowRuleEvaluationContext(roots));
    }
}
