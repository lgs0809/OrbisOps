package cn.lgs.orbisops.trigger.application.mcp;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMcpRuntimePayloadSanitizerAdapterTest {

    private final OpsMcpRuntimePayloadSanitizerAdapter sanitizer =
            new OpsMcpRuntimePayloadSanitizerAdapter();

    @Test
    @SuppressWarnings("unchecked")
    void nestedStructuredPayloadMasksProtectedKeys() {
        String sanitized = sanitizer.sanitize(Map.of(
                "query", "5xx",
                "connection", Map.of("authHeader", "sample-value", "region", "north")));
        Map<String, Object> decoded = JSON.parseObject(sanitized, Map.class);
        Map<String, Object> connection = (Map<String, Object>) decoded.get("connection");

        assertEquals("5xx", decoded.get("query"));
        assertEquals("***", connection.get("authHeader"));
        assertEquals("north", connection.get("region"));
    }

    @Test
    void plainTextPayloadMasksProtectedAssignmentsWithoutDroppingOtherText() {
        String sanitized = sanitizer.sanitize("query=5xx authHeader=sample-value region=north");

        assertTrue(sanitized.contains("query=5xx"));
        assertTrue(sanitized.contains("authHeader=***"));
        assertTrue(sanitized.contains("region=north"));
        assertFalse(sanitized.contains("sample-value"));
    }
}
