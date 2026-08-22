package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentOutputGuardTest {

    @Test
    void rejectsHttpErrorEnvelopeReturnedAsAssistantText() {
        assertThrows(IllegalStateException.class, () -> OpsAgentOutputGuard.assertSuccessful(
                "Exception: 422 - {\"detail\":[{\"msg\":\"Field required\"}]}"));
    }

    @Test
    void rejectsGraphFailureEnvelope() {
        assertThrows(IllegalStateException.class,
                () -> OpsAgentOutputGuard.assertSuccessful("Graph 执行失败：工具定义无效"));
    }

    @Test
    void rejectsProviderEofReturnedAsAssistantText() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OpsAgentOutputGuard.assertSuccessful(
                        "Exception: I/O error on POST request for \"https://model.example.com/v1/chat/completions\": EOF reached while reading"));
        assertTrue(error.getMessage().startsWith("MODEL_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void preservesProviderQuotaReasonFromAssistantErrorEnvelope() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OpsAgentOutputGuard.assertSuccessful(
                        "Exception: 403 - {\"code\":\"INSUFFICIENT_BALANCE\",\"message\":\"Insufficient account balance\"}"));
        assertTrue(error.getMessage().startsWith("MODEL_PROVIDER_QUOTA_EXHAUSTED"));
    }

    @Test
    void preservesProviderAuthReasonFromAssistantErrorEnvelope() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OpsAgentOutputGuard.assertSuccessful(
                        "Exception: 401 - {\"message\":\"Unauthorized\"}"));
        assertTrue(error.getMessage().startsWith("MODEL_PROVIDER_AUTH_FAILED"));
    }

    @Test
    void rejectsConnectionResetReturnedAsAssistantText() {
        assertThrows(IllegalStateException.class, () -> OpsAgentOutputGuard.assertSuccessful(
                "Error: connection reset by peer while calling model provider"));
    }

    @Test
    void exposesTransportEnvelopeClassificationForSafeRecoveryPolicy() {
        assertTrue(OpsAgentOutputGuard.isTransportErrorEnvelope(
                "Exception: I/O error on POST request for \"https://provider.example/v1/chat/completions\": Request timed out"));
        assertTrue(OpsAgentOutputGuard.isTransportErrorEnvelope(
                "model wrapper output:\nException: I/O error on POST request for \"https://provider.example/v1/chat/completions\": Remote host terminated the handshake"));
        assertFalse(OpsAgentOutputGuard.isTransportErrorEnvelope(
                "Exception: 422 - {\"detail\":\"invalid request\"}"));
    }

    @Test
    void acceptsNormalExceptionAnalysis() {
        assertDoesNotThrow(() -> OpsAgentOutputGuard.assertSuccessful(
                "结论：最近日志中的 NullPointerException 来自库存规则为空，需要继续补充真实日志证据。"));
    }
}
