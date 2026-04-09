package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertEventRepository;
import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEventApplicationServiceTest {

    @Test
    void clampsListLimitBeforeRepositoryCall() {
        CapturingRepository repository = new CapturingRepository();
        AlertEventApplicationService service = new AlertEventApplicationService(repository, event -> { });

        service.list(999);

        assertEquals(200, repository.listLimit);
    }

    @Test
    void appendsTerminalEventAndIngestsEligibleIncident() {
        CapturingRepository repository = new CapturingRepository();
        List<AlertEventSnapshot> incidents = new ArrayList<>();
        AlertEventApplicationService service = new AlertEventApplicationService(repository, incidents::add);

        AlertEventSnapshot result = service.append(draft("TRIGGERED", "FAILED"));

        assertTrue(repository.appendCompleted);
        assertEquals("TRIGGERED", result.status());
        assertEquals(List.of(result), incidents);
    }

    @Test
    void appendsNonEligibleEventWithoutIncident() {
        CapturingRepository repository = new CapturingRepository();
        List<AlertEventSnapshot> incidents = new ArrayList<>();
        AlertEventApplicationService service = new AlertEventApplicationService(repository, incidents::add);

        service.append(draft("REJECTED", "PENDING"));

        assertFalse(repository.appendCompleted);
        assertTrue(incidents.isEmpty());
    }

    @Test
    void updatesSupportedRunOutcomeAndMarksTerminalCompletion() {
        CapturingRepository repository = new CapturingRepository();
        AlertEventApplicationService service = new AlertEventApplicationService(repository, event -> { });
        AlertRunOutcome outcome = new AlertRunOutcome(
                "ALERTMANAGER", "fp-1", "run-1", "SUCCEEDED", "done", "");

        service.updateRunOutcome(outcome);

        assertEquals(outcome, repository.outcome);
        assertTrue(repository.outcomeCompleted);
    }

    @Test
    void ignoresUnsupportedSourceAndMissingFingerprint() {
        CapturingRepository repository = new CapturingRepository();
        AlertEventApplicationService service = new AlertEventApplicationService(repository, event -> { });

        service.updateRunOutcome(new AlertRunOutcome(
                "MANUAL", "fp-1", "run-1", "FAILED", "", "error"));
        service.updateRunOutcome(new AlertRunOutcome(
                "ALERTMANAGER", "", "run-1", "FAILED", "", "error"));

        assertNull(repository.outcome);
    }

    private AlertEventDraft draft(String status, String runStatus) {
        return new AlertEventDraft(
                7L, "Payment Alert", "project-1", "ALERTMANAGER", status,
                "dispatch-1", "fp-1", "HighErrorRate", "critical", "payment", "ops",
                "run-1", runStatus, "", "", Map.of("env", "prod"), Map.of(), Map.of());
    }

    private static final class CapturingRepository implements IAlertEventRepository {
        private int listLimit;
        private boolean appendCompleted;
        private AlertRunOutcome outcome;
        private boolean outcomeCompleted;

        @Override
        public List<AlertEventSnapshot> list(int limit) {
            listLimit = limit;
            return List.of();
        }

        @Override
        public AlertEventSnapshot append(AlertEventDraft draft, boolean completed) {
            appendCompleted = completed;
            return new AlertEventSnapshot(
                    1L, draft.ruleId(), draft.ruleName(), draft.projectId(), draft.sourceType(), draft.status(),
                    draft.dispatchKey(), draft.fingerprint(), draft.alertName(), draft.severity(), draft.serviceName(),
                    draft.receiver(), draft.runId(), draft.runStatus(), draft.finalSummary(), completed ? "now" : "",
                    draft.errorMessage(), draft.labels(), draft.annotations(), draft.payload(), "now");
        }

        @Override
        public void updateRunOutcome(AlertRunOutcome outcome, boolean completed) {
            this.outcome = outcome;
            this.outcomeCompleted = completed;
        }
    }
}
