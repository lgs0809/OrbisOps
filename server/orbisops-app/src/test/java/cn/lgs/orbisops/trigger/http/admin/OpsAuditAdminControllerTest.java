package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.audit.AnalysisAuditApplicationService;
import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import cn.lgs.orbisops.trigger.application.audit.OpsAnalysisAuditMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAuditAdminControllerTest {

    @Test
    void mapsTypedAuditRecordsAndForwardsRequestedLimit() {
        AnalysisAuditApplicationService audits = mock(AnalysisAuditApplicationService.class);
        when(audits.list(20)).thenReturn(List.of(record("analysis-1")));
        OpsAuditAdminController controller = new OpsAuditAdminController(audits, new OpsAnalysisAuditMapper());

        var response = controller.listAuditRecords(null);

        assertEquals("analysis-1", response.getData().get(0).getAnalysisId());
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(audits).list(limit.capture());
        assertEquals(20, limit.getValue());
    }

    private AnalysisAuditRecord record(String id) {
        return new AnalysisAuditRecord(
                id, true, "question", "intent", 15, "5m", "now", 10L,
                List.of("ES"), List.of("ES"), List.of("PROM"), Map.of("ES", "FOUND"),
                List.of("WARN"), "conclusion", "");
    }
}
