package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpToolArgumentPolicyCheckerTest {

    private final OpsMcpToolArgumentPolicyChecker checker = new OpsMcpToolArgumentPolicyChecker();

    @Test
    void regexDenylistSupportsPerFieldPatternList() {
        Map<String, Object> policy = Map.of(
                "requiredKeys", List.of("query"),
                "regexDenylist", Map.of("query", List.of("(?i)delete|update|insert|drop|alter|truncate")));

        assertDoesNotThrow(() -> checker.assertAllowed(policy, "{\"query\":\"up\"}", OpsToolCallStage.PREPARE));
        assertThrows(SecurityException.class,
                () -> checker.assertAllowed(policy, "{\"query\":\"delete from t\"}", OpsToolCallStage.PREPARE));
    }
}
