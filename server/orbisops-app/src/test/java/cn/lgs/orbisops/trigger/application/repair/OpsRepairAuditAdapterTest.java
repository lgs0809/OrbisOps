package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.RepairAuditEvent;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OpsRepairAuditAdapterTest {

    @Test
    void mapsTypedRepairEventToConfigAuditContract() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsRepairAuditAdapter adapter = new OpsRepairAuditAdapter(audits);
        Map<String, Object> before = Map.of("status", "ACTIVE");
        Map<String, Object> after = Map.of("status", "VERIFIED");

        adapter.record(new RepairAuditEvent(
                "project-1", "verify", "repair-1", before, after));
        adapter.record(null);

        verify(audits).record(
                "project-1", "repair-workspace", "verify", "repair-1", before, after);
        verify(audits, never()).record(
                "", "repair-workspace", "verify", "repair-1", before, after);
    }
}
