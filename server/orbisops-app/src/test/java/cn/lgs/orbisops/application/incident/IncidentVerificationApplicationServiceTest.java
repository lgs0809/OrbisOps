package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentVerificationApplicationServiceTest {

    @Test
    void onlyAuthoritativePortCanProduceVerificationSuccessFact() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentVerificationPort verification = mock(IncidentVerificationPort.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        CountingTransactions transactions = new CountingTransactions();
        IncidentSnapshot incident = snapshot();
        when(repository.find("incident-1")).thenReturn(Optional.of(incident));
        when(verification.verify(incident, "pkg-1")).thenReturn(new IncidentVerificationResult(
                IncidentVerificationResult.Status.PASSED,
                "恢复指标已核验通过",
                Map.of("resultId", "result-1", "outputHash", "hash-1")));
        IncidentVerificationApplicationService service = new IncidentVerificationApplicationService(
                repository, verification, audit, transactions);

        IncidentVerificationResult result = service.verify("incident-1", "pkg-1", "alice");

        assertEquals(IncidentVerificationResult.Status.PASSED, result.status());
        assertEquals(2, transactions.count);
        ArgumentCaptor<IncidentTimelineDraft> timeline = ArgumentCaptor.forClass(IncidentTimelineDraft.class);
        verify(repository, org.mockito.Mockito.times(2)).appendTimeline(timeline.capture());
        assertEquals(List.of("VERIFICATION_STARTED", "VERIFICATION_SUCCEEDED"),
                timeline.getAllValues().stream().map(IncidentTimelineDraft::eventType).toList());
        verify(audit).record(any(IncidentAuditEvent.class));
    }

    private IncidentSnapshot snapshot() {
        return new IncidentSnapshot(
                1L, "incident-1", "project-a", "payment errors", IncidentStatus.VERIFYING,
                "WARNING", "payment", "ALERT", "", "", "", "", "", "{}", "{}", 1,
                "[]", "", "", "", "", "", "", "");
    }

    private static final class CountingTransactions implements IncidentTransactionPort {
        private int count;

        @Override
        public <T> T required(Supplier<T> action) {
            count++;
            return action.get();
        }
    }
}
