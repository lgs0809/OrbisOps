package cn.lgs.orbisops.application.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientApiHealthCheckUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T03:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void missingTargetIsAuditedWithoutProbeOrHistoryRecord() {
        AiClientApiHealthTargetPort targets = mock(AiClientApiHealthTargetPort.class);
        AiClientApiHealthProbePort probe = mock(AiClientApiHealthProbePort.class);
        AiClientApiHealthOperatorPort operator = mock(AiClientApiHealthOperatorPort.class);
        AiClientApiHealthRecordPort records = mock(AiClientApiHealthRecordPort.class);
        AiClientApiHealthAuditPort audit = mock(AiClientApiHealthAuditPort.class);
        AiClientApiHealthCheckUseCase useCase = new AiClientApiHealthCheckUseCase(
                targets, probe, operator, records, audit, CLOCK);
        when(operator.currentOperator()).thenReturn("admin-1");
        when(targets.find("missing-provider")).thenReturn(null);

        AiClientApiHealthCheckResult result = useCase.check("missing-provider");

        assertEquals("FAILED", result.status());
        assertEquals("未找到对应的 API Provider", result.errorMessage());
        assertEquals("admin-1", result.testedBy());
        assertEquals(LocalDateTime.of(2026, 7, 30, 3, 0), result.checkedAt());
        assertNull(result.httpStatus());
        verify(probe, never()).probe(org.mockito.ArgumentMatchers.any());
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
        verify(audit).record(result);
    }

    @Test
    void existingTargetIsProbedThenRecordedThenAudited() {
        AiClientApiHealthTargetPort targets = mock(AiClientApiHealthTargetPort.class);
        AiClientApiHealthProbePort probe = mock(AiClientApiHealthProbePort.class);
        AiClientApiHealthOperatorPort operator = mock(AiClientApiHealthOperatorPort.class);
        AiClientApiHealthRecordPort records = mock(AiClientApiHealthRecordPort.class);
        AiClientApiHealthAuditPort audit = mock(AiClientApiHealthAuditPort.class);
        AiClientApiHealthCheckUseCase useCase = new AiClientApiHealthCheckUseCase(
                targets, probe, operator, records, audit, CLOCK);
        AiClientApiHealthTarget target = new AiClientApiHealthTarget(
                "local-provider", "http://127.0.0.1:8080/v1", "v1/chat/completions", "api-key");
        AiClientApiHealthProbeOutcome outcome = new AiClientApiHealthProbeOutcome(
                "http://127.0.0.1:8080/v1/models", "SUCCESS", 200, 12L, "");
        when(operator.currentOperator()).thenReturn("admin-1");
        when(targets.find("local-provider")).thenReturn(target);
        when(probe.probe(target)).thenReturn(outcome);

        AiClientApiHealthCheckResult result = useCase.check("local-provider");

        InOrder order = inOrder(probe, records, audit);
        order.verify(probe).probe(target);
        order.verify(records).save(result);
        order.verify(audit).record(result);
        assertEquals("local-provider", result.apiId());
        assertEquals("MODELS_ENDPOINT", result.testType());
        assertEquals("SUCCESS", result.status());
        assertEquals(200, result.httpStatus());
        assertEquals(12L, result.latencyMs());
    }

    @Test
    void nullProbeOutcomeDegradesToTypedFailureAndStillRecordsHistory() {
        AiClientApiHealthTargetPort targets = mock(AiClientApiHealthTargetPort.class);
        AiClientApiHealthProbePort probe = mock(AiClientApiHealthProbePort.class);
        AiClientApiHealthOperatorPort operator = mock(AiClientApiHealthOperatorPort.class);
        AiClientApiHealthRecordPort records = mock(AiClientApiHealthRecordPort.class);
        AiClientApiHealthAuditPort audit = mock(AiClientApiHealthAuditPort.class);
        AiClientApiHealthCheckUseCase useCase = new AiClientApiHealthCheckUseCase(
                targets, probe, operator, records, audit, CLOCK);
        AiClientApiHealthTarget target = new AiClientApiHealthTarget(
                "local-provider", "http://127.0.0.1:8080/v1", "v1/chat/completions", "api-key");
        when(targets.find("local-provider")).thenReturn(target);
        when(probe.probe(target)).thenReturn(null);

        AiClientApiHealthCheckResult result = useCase.check("local-provider");

        assertEquals("FAILED", result.status());
        assertEquals("健康检查未返回结果", result.errorMessage());
        ArgumentCaptor<AiClientApiHealthCheckResult> saved = ArgumentCaptor.forClass(AiClientApiHealthCheckResult.class);
        verify(records).save(saved.capture());
        assertEquals(result, saved.getValue());
        verify(audit).record(result);
    }
}
