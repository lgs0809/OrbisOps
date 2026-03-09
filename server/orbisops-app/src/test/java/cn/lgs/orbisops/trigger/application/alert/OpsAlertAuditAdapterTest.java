package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertRuleAuditEvent;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAlertAuditAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void translatesTypedAuditEventIntoConfigAuditEnvelope() {
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsAlertAuditAdapter adapter = new OpsAlertAuditAdapter(auditService);
        AlertRuleDefinition before = definition(7L, 1);
        AlertRuleDefinition after = definition(7L, 0);

        adapter.record(new AlertRuleAuditEvent("status", "7", before, after, "alice"));

        ArgumentCaptor<Map<String, Object>> envelope = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(
                eq("alert-trigger"), eq("status"), eq("7"), eq(before), envelope.capture());
        assertSame(after, envelope.getValue().get("result"));
        assertEquals("alice", envelope.getValue().get("actor"));
    }

    private AlertRuleDefinition definition(Long id, int status) {
        return new AlertRuleDefinition(
                id, "Payment Alert", status, "ALERTMANAGER", "", "", "", Map.of(),
                "", "", "secret", "project-1", "agent-1", "LATEST_PUBLISHED", 4,
                "agent-v4", "", 30, "5m", true, false, 5, 120, 20, 300,
                "created", "updated");
    }
}
