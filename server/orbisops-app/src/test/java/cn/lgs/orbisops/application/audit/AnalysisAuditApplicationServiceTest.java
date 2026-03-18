package cn.lgs.orbisops.application.audit;

import cn.lgs.orbisops.domain.audit.adapter.repository.IAnalysisAuditRepository;
import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnalysisAuditApplicationServiceTest {

    @Test
    void appendsToRepositoryAndKeepsBoundedNewestFirstFallback() {
        FakeRepository repository = new FakeRepository();
        AnalysisAuditApplicationService service = new AnalysisAuditApplicationService(repository, 2);

        service.append(record("analysis-1"));
        service.append(record("analysis-2"));
        service.append(record("analysis-3"));

        assertEquals(List.of("analysis-1", "analysis-2", "analysis-3"),
                repository.upserts.stream().map(AnalysisAuditRecord::analysisId).toList());
        assertEquals(List.of("analysis-3", "analysis-2"),
                service.list(10).stream().map(AnalysisAuditRecord::analysisId).toList());
    }

    @Test
    void prefersPersistedRowsAndClampsRequestedLimit() {
        FakeRepository repository = new FakeRepository();
        AnalysisAuditRecord persisted = record("persisted");
        repository.persisted = List.of(persisted);
        AnalysisAuditApplicationService service = new AnalysisAuditApplicationService(repository, 10);
        service.append(record("memory"));

        List<AnalysisAuditRecord> result = service.list(999);

        assertEquals(100, repository.listLimit);
        assertEquals(1, result.size());
        assertSame(persisted, result.get(0));
    }

    @Test
    void ignoresNullAppendAndNormalizesZeroCapacity() {
        FakeRepository repository = new FakeRepository();
        AnalysisAuditApplicationService service = new AnalysisAuditApplicationService(repository, 0);

        service.append(null);
        service.append(record("analysis-1"));
        service.append(record("analysis-2"));

        assertEquals(List.of("analysis-2"),
                service.list(10).stream().map(AnalysisAuditRecord::analysisId).toList());
        assertEquals(2, repository.upserts.size());
    }

    private AnalysisAuditRecord record(String analysisId) {
        return new AnalysisAuditRecord(
                analysisId, true, "question", "intent", 15, "5m", "now", 10L,
                List.of("ES"), List.of("ES"), List.of("PROM"), Map.of("ES", "FOUND"),
                List.of("WARN"), "conclusion", "");
    }

    private static final class FakeRepository implements IAnalysisAuditRepository {
        private final List<AnalysisAuditRecord> upserts = new ArrayList<>();
        private List<AnalysisAuditRecord> persisted = List.of();
        private int listLimit;

        @Override
        public void upsert(AnalysisAuditRecord record) {
            upserts.add(record);
        }

        @Override
        public List<AnalysisAuditRecord> list(int limit) {
            listLimit = limit;
            return persisted;
        }
    }
}
