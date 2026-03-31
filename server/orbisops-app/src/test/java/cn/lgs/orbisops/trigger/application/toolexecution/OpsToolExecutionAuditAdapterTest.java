package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OpsToolExecutionAuditAdapterTest {

    @Test
    void shouldMapTypedAuditEventToConfigAuditBoundary() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsToolExecutionAuditAdapter adapter = new OpsToolExecutionAuditAdapter(audits);
        Map<String, Object> payload = Map.of("runId", "run-1", "decision", "ALLOWED");

        adapter.record(new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                "project-1", "allowed", "code.repair/code_bash", payload));

        verify(audits).recordIdempotent(
                "project-1", "tool-execution", "allowed",
                "code.repair/code_bash", null, payload, "");
    }

    @Test
    void projectionPayloadMustUseStableAuditDeliveryKey() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsToolExecutionAuditAdapter adapter = new OpsToolExecutionAuditAdapter(audits);
        Map<String, Object> payload = Map.of(
                "runId", "run-1",
                "projectionId", "projection-1");

        adapter.record(new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                "project-1", "completed", "code.repair/code_bash", payload));

        verify(audits).recordIdempotent(
                "project-1", "tool-execution", "completed",
                "code.repair/code_bash", null, payload,
                "tool-completion-audit:projection-1");
    }

    @Test
    void nullEventMustNotReachLegacyAuditService() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);

        new OpsToolExecutionAuditAdapter(audits).record(null);

        verifyNoInteractions(audits);
    }
}
