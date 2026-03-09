package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsConfigAuditAdminControllerTest {

    @Test
    void listBuildsCompatibilityQueryAndReturnsRows() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        when(audits.search(any(OpsConfigAuditService.AuditQuery.class)))
                .thenReturn(List.of(Map.of("audit_id", "audit-1")));
        OpsConfigAuditAdminController controller = new OpsConfigAuditAdminController(audits);

        List<Map<String, Object>> rows = controller.listConfigAudits(
                "project-1", "alice", "agent-1", "skill", "update", "MEDIUM",
                "2026-07-01", "2026-07-31", 50).getData();

        assertEquals("audit-1", rows.get(0).get("audit_id"));
        ArgumentCaptor<OpsConfigAuditService.AuditQuery> query =
                ArgumentCaptor.forClass(OpsConfigAuditService.AuditQuery.class);
        verify(audits).search(query.capture());
        assertEquals("project-1", query.getValue().projectId());
        assertEquals("skill", query.getValue().moduleName());
        assertEquals(50, query.getValue().limit());
    }

    @Test
    void policyUpdateFailureKeepsHttpFailureEnvelope() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        when(audits.updatePolicy(any())).thenThrow(new IllegalStateException("policy store down"));
        OpsConfigAuditAdminController controller = new OpsConfigAuditAdminController(audits);

        var response = controller.updateAuditPolicy(Map.of("projectId", "project-1"));

        assertEquals("policy store down", response.getInfo());
        assertNull(response.getData());
    }
}
