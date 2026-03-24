package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.rule.AllRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.AnyRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.ChangedRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.ComparisonRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.ExistsRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.InRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.NotRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowComparisonOperator;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluationContext;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluator;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleField;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleLimits;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRuleEvaluatorTest {

    private final WorkflowRuleEvaluator evaluator = new WorkflowRuleEvaluator();

    @Test
    void compositeAndComparisonRulesMustUseDeterministicNumericSemantics() {
        WorkflowRule rule = new AllRule(List.of(
                new ComparisonRule(field("runtime.round"), WorkflowComparisonOperator.GTE, 2),
                new ComparisonRule(field("runtime.round"), WorkflowComparisonOperator.LT, 5),
                new AnyRule(List.of(
                        new ComparisonRule(field("approval.status"), WorkflowComparisonOperator.EQ, "APPROVED"),
                        new ExistsRule(field("error.message")))),
                new NotRule(new ComparisonRule(
                        field("runtime.cancelled"), WorkflowComparisonOperator.EQ, true))));
        WorkflowRuleEvaluationContext context = context(Map.of(
                "runtime", Map.of("round", new BigDecimal("2.0"), "cancelled", false),
                "approval", Map.of("status", "APPROVED"),
                "error", Map.of()));

        assertTrue(evaluator.evaluate(rule, context));
    }

    @Test
    void missingAndNullFieldsMustFailClosedExceptExplicitNullEquality() {
        WorkflowRuleEvaluationContext context = context(Map.of(
                "runtime", mapWithNull("nullable"),
                "approval", Map.of()));

        assertFalse(evaluator.evaluate(
                new ComparisonRule(field("runtime.missing"), WorkflowComparisonOperator.NE, "x"),
                context));
        assertTrue(evaluator.evaluate(
                new ComparisonRule(field("runtime.nullable"), WorkflowComparisonOperator.EQ, null),
                context));
        assertFalse(evaluator.evaluate(new ExistsRule(field("runtime.nullable")), context));
        assertFalse(evaluator.evaluate(new ExistsRule(field("runtime.missing")), context));
    }

    @Test
    void inRuleMustSupportCollectionMembershipAndBoundedStringContains() {
        WorkflowRuleEvaluationContext context = context(Map.of(
                "runtime", Map.of("routes", List.of("prometheus", "rag")),
                "nodeOutput", Map.of("output", "database timeout detected")));

        assertTrue(evaluator.evaluate(
                new InRule(field("runtime.routes"), List.of("rag")), context));
        assertTrue(evaluator.evaluate(
                new InRule(field("nodeOutput.output"), List.of("timeout")), context));
        assertFalse(evaluator.evaluate(
                new InRule(field("runtime.routes"), List.of("mysql")), context));
    }

    @Test
    void changedRuleRequiresBothCurrentAndPreviousValues() {
        WorkflowRuleField field = field("runtime.status");
        WorkflowRuleEvaluationContext changed = new WorkflowRuleEvaluationContext(
                Map.of("runtime", Map.of("status", "FAILED")),
                Map.of("runtime", Map.of("status", "RUNNING")));
        WorkflowRuleEvaluationContext missingPrevious = new WorkflowRuleEvaluationContext(
                Map.of("runtime", Map.of("status", "FAILED")), Map.of());

        assertTrue(evaluator.evaluate(new ChangedRule(field), changed));
        assertFalse(evaluator.evaluate(new ChangedRule(field), missingPrevious));
    }

    @Test
    void evaluatorMustNeverReflectIntoArbitraryObjects() {
        Object object = new Object() {
            @SuppressWarnings("unused")
            public String getSecret() {
                return "leak";
            }
        };
        WorkflowRuleEvaluationContext context = context(Map.of(
                "runtime", Map.of("object", object)));

        assertFalse(evaluator.evaluate(
                new ComparisonRule(
                        field("runtime.object.secret"),
                        WorkflowComparisonOperator.EQ,
                        "leak"),
                context));
        assertFalse(evaluator.evaluate(
                new ComparisonRule(
                        field("runtime.object"),
                        WorkflowComparisonOperator.NE,
                        "safe"),
                context));
    }

    @Test
    void fieldCatalogMustRejectDynamicPathsAndUnknownRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowRuleField("system.env.PATH"));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowRuleField("runtime.items[0]"));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowRuleField("runtime.getClass()"));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkflowRuleField("runtime"));
    }

    @Test
    void depthAndNodeLimitsMustFailClosed() {
        WorkflowRule deep = new ComparisonRule(
                field("runtime.value"), WorkflowComparisonOperator.EQ, true);
        for (int index = 0; index < 5; index++) deep = new NotRule(deep);
        WorkflowRuleEvaluator depthLimited = new WorkflowRuleEvaluator(
                new WorkflowRuleLimits(3, 100));
        WorkflowRule deepRule = deep;
        assertThrows(IllegalArgumentException.class,
                () -> depthLimited.evaluate(deepRule, context(Map.of("runtime", Map.of("value", true)))));

        List<WorkflowRule> many = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            many.add(new ExistsRule(field("runtime.value")));
        }
        WorkflowRuleEvaluator nodeLimited = new WorkflowRuleEvaluator(
                new WorkflowRuleLimits(10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> nodeLimited.evaluate(
                        new AllRule(many),
                        context(Map.of("runtime", Map.of("value", true)))));
    }

    private WorkflowRuleField field(String path) {
        return new WorkflowRuleField(path);
    }

    private WorkflowRuleEvaluationContext context(Map<String, Object> roots) {
        return new WorkflowRuleEvaluationContext(roots);
    }

    private Map<String, Object> mapWithNull(String key) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put(key, null);
        return map;
    }
}
