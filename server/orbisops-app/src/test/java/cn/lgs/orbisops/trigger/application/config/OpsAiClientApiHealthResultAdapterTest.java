package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiHealthCheckRepository;
import cn.lgs.orbisops.domain.agent.model.valobj.AiClientApiHealthCheckRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAiClientApiHealthResultAdapterTest {

    @Test
    void persistsTypedResultAndTruncatesOversizedErrorHistory() {
        IAiClientApiHealthCheckRepository repository = mock(IAiClientApiHealthCheckRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiHealthResultAdapter adapter = new OpsAiClientApiHealthResultAdapter(repository, audit);
        LocalDateTime checkedAt = LocalDateTime.of(2026, 7, 30, 3, 0);
        AiClientApiHealthCheckResult result = new AiClientApiHealthCheckResult(
                "local-provider",
                "MODELS_ENDPOINT",
                "http://127.0.0.1:8080/v1/models",
                "FAILED",
                500,
                18L,
                "x".repeat(1200),
                "admin-1",
                checkedAt,
                checkedAt);

        adapter.save(result);

        ArgumentCaptor<AiClientApiHealthCheckRecord> record =
                ArgumentCaptor.forClass(AiClientApiHealthCheckRecord.class);
        verify(repository).save(record.capture());
        assertEquals("local-provider", record.getValue().apiId());
        assertEquals("MODELS_ENDPOINT", record.getValue().testType());
        assertEquals(1000, record.getValue().errorMessage().length());
        assertEquals("admin-1", record.getValue().testedBy());
    }

    @Test
    void auditsTypedHealthResultThroughCompatibilityFacade() {
        IAiClientApiHealthCheckRepository repository = mock(IAiClientApiHealthCheckRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiHealthResultAdapter adapter = new OpsAiClientApiHealthResultAdapter(repository, audit);
        LocalDateTime checkedAt = LocalDateTime.of(2026, 7, 30, 3, 0);
        AiClientApiHealthCheckResult result = new AiClientApiHealthCheckResult(
                "local-provider", "MODELS_ENDPOINT", "endpoint", "SUCCESS",
                200, 10L, "", "admin-1", checkedAt, checkedAt);

        adapter.record(result);

        verify(audit).record(
                "model-provider-health",
                "health-check",
                "local-provider",
                null,
                result);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
