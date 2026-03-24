package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DirectActionDataPolicyTest {
    private final DirectActionDataPolicy policy = new DirectActionDataPolicy();

    @Test void bindsTypedInputAndPreviousEvidenceWithoutStringInterpolation() {
        var action = Map.of("arguments", Map.of("selector", "literal-${input.service}"),
                "argumentBindings", Map.of("service", "input.service", "value", "nodeOutput.workflowData_metrics.count"));
        assertEquals(Map.of("selector", "literal-${input.service}", "service", "orders", "value", 101),
                policy.arguments(action, Map.of("service", "orders"), Map.of("workflowData_metrics", Map.of("count", 101))));
    }

    @Test void refusesAuthorityRootsAndExpressionSyntax() {
        for (String field : List.of("runtime.landingApproved", "approval.decision", "nodeOutput.runId", "nodeOutput.workflowData",
                "input.orders[0]", "input.getClass()", "T(java.lang.Runtime)")) {
            assertThrows(IllegalArgumentException.class,
                    () -> policy.validate(Map.of("argumentBindings", Map.of("value", field))), field);
        }
    }

    @Test void missingOrNullEvidenceCannotBecomeAHealthyDefault() {
        var action = Map.of("argumentBindings", Map.of("value", "nodeOutput.workflowData_metrics.requestCount"));
        assertThrows(IllegalArgumentException.class, () -> policy.arguments(action, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> policy.arguments(action, Map.of(), Map.of("workflowData_metrics", Map.of("errorRate", 0))));
    }

    @Test void refusesAmbiguousBindingsAndInvalidKeys() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate(Map.of("arguments", Map.of("target", "fixed"),
                "argumentBindings", Map.of("target", "input.target"))));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(Map.of("arguments", "raw",
                "argumentBindings", Map.of("target", "input.target"))));
        for (Object key : List.of("", "a.b", "${input}", 42))
            assertThrows(IllegalArgumentException.class, () -> policy.validate(Map.of("structuredOutputKey", key)));
    }

    @Test void acceptsOnlyBoundedStandardJsonObjects() {
        for (String value : List.of("", "null", "[]", "{bad}", "{\"a\":1} trailing", "{".repeat(33), "x".repeat(1_048_577)))
            assertThrows(IllegalArgumentException.class, () -> policy.parseObject(value));
        assertEquals(Map.of("message", "{ bracket inside string [", "count", 100),
                policy.parseObject("{\"message\":\"{ bracket inside string [\",\"count\":100}"));
    }

    @Test void callbackArgumentsCannotMutatePreviousEvidence() {
        var prior = new java.util.LinkedHashMap<String,Object>(Map.of("count",100));
        var action = Map.of("argumentBindings", Map.of("evidence", "nodeOutput.workflowData_metrics"));
        @SuppressWarnings("unchecked") var bound = (Map<String,Object>) policy.arguments(action, Map.of(), Map.of("workflowData_metrics",prior));
        @SuppressWarnings("unchecked") var evidence = (Map<String,Object>) bound.get("evidence");
        evidence.put("count",0);
        assertEquals(100,prior.get("count"));
    }
}
