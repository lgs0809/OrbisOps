package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAiClientApiHealthResultAdapterTest {

    @Test
    void auditsTypedHealthResultWithoutPersistenceCompatibilityFacade() {
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiHealthResultAdapter adapter = new OpsAiClientApiHealthResultAdapter(audit);
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
    }
}
