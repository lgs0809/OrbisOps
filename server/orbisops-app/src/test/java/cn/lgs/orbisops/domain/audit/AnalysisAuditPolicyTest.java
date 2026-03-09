package cn.lgs.orbisops.domain.audit;

import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import cn.lgs.orbisops.domain.audit.service.AnalysisAuditPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisAuditPolicyTest {

    @Test
    void clampsListLimitAndNormalizesCapacity() {
        AnalysisAuditPolicy policy = new AnalysisAuditPolicy();

        assertEquals(1, policy.listLimit(0));
        assertEquals(20, policy.listLimit(20));
        assertEquals(100, policy.listLimit(999));
        assertEquals(1, policy.historyCapacity(0));
        assertEquals(200, policy.historyCapacity(200));
    }

    @Test
    void recordRequiresIdentityRejectsNegativeDurationAndCopiesCollections() {
        List<String> selected = new ArrayList<>(List.of("ES"));
        selected.add(null);
        selected.add("  ");
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("ES", null);
        statuses.put(null, "IGNORED");
        AnalysisAuditRecord record = new AnalysisAuditRecord(
                " analysis-1 ", true, "question", "intent", 15, "5m", "now", 10L,
                selected, List.of(), List.of(), statuses, List.of("WARN"),
                "conclusion", "");
        selected.add("PROM");

        assertEquals("analysis-1", record.analysisId());
        assertEquals(List.of("ES"), record.selectedSources());
        assertEquals(Map.of("ES", ""), record.resultStatuses());
        assertThrows(UnsupportedOperationException.class, () -> record.selectedSources().add("PROM"));
        assertEquals("ANALYSIS_AUDIT_ID_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> new AnalysisAuditRecord(
                                "", true, "", "", null, "", "", 1L,
                                List.of(), List.of(), List.of(), Map.of(), List.of(), "", ""))
                        .getMessage());
        assertEquals("ANALYSIS_AUDIT_DURATION_INVALID:-1",
                assertThrows(IllegalArgumentException.class,
                        () -> new AnalysisAuditRecord(
                                "analysis-2", false, "", "", null, "", "", -1L,
                                List.of(), List.of(), List.of(), Map.of(), List.of(), "", ""))
                        .getMessage());
    }
}
