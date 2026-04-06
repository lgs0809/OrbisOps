package cn.lgs.orbisops.trigger.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsSseFailurePayloadTest {

    @Test
    void failedPayloadNeverContainsNullValues() {
        Map<String, Object> payload = OpsSseFailurePayload.failed(new IllegalStateException());

        assertEquals("ERROR", payload.get("eventType"));
        assertEquals("FAILED", payload.get("status"));
        assertEquals("IllegalStateException", payload.get("summary"));
    }

    @Test
    void summaryUsesFirstNonBlankCauseMessage() {
        RuntimeException failure = new RuntimeException(" ", new IllegalArgumentException("invalid request"));

        assertEquals("invalid request", OpsSseFailurePayload.summary(failure));
    }

    @Test
    void summaryFallsBackForMissingFailure() {
        assertEquals("Agent 流式处理失败", OpsSseFailurePayload.summary(null));
    }
}
