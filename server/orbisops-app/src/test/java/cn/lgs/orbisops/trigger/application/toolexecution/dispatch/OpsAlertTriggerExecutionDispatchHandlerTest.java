package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertTriggerExecutionDispatchHandlerTest {

    @Test
    void crossProjectMutationMustFailClosedBeforeDelete() {
        AlertRuleManagementApplicationService rules = mock(AlertRuleManagementApplicationService.class);
        when(rules.list()).thenReturn(List.of(rule(7L, "project-2")));
        OpsAlertTriggerExecutionDispatchHandler handler = handler(rules);

        assertThrows(SecurityException.class, () -> handler.dispatch(
                target("alert_trigger_delete"), request(Map.of("id", 7L))));

        verify(rules, never()).delete(any(), any());
    }

    @Test
    void shouldMapDefaultsIntoTypedCandidateAndPreserveActor() {
        AlertRuleManagementApplicationService rules = mock(AlertRuleManagementApplicationService.class);
        when(rules.save(any(AlertRuleCandidate.class), eq("alice")))
                .thenReturn(rule(8L, "project-1"));
        OpsAlertTriggerExecutionDispatchHandler handler = handler(rules);

        handler.dispatch(target("alert_trigger_create"), request(Map.of()));

        ArgumentCaptor<AlertRuleCandidate> candidate = ArgumentCaptor.forClass(AlertRuleCandidate.class);
        verify(rules).save(candidate.capture(), eq("alice"));
        assertEquals("project-1", candidate.getValue().projectId());
        assertEquals("generic-ops-react-agent", candidate.getValue().agentDefinitionId());
        assertEquals(15, candidate.getValue().rangeMinutes());
        assertEquals("15m", candidate.getValue().promWindow());
    }

    private OpsAlertTriggerExecutionDispatchHandler handler(
            AlertRuleManagementApplicationService rules) {
        return new OpsAlertTriggerExecutionDispatchHandler(
                rules,
                mock(AlertEventApplicationService.class),
                new OpsAlertRuleMapper(),
                new OpsAlertEventMapper());
    }

    private AlertRuleDefinition rule(Long id, String projectId) {
        return new AlertRuleDefinition(
                id, "rule", 1, "ALERTMANAGER", ".*", ".*", ".*", Map.of(),
                "", "", "", projectId, "generic-ops-react-agent", "LATEST_PUBLISHED",
                null, "", "question", 15, "15m", true, false,
                3, 60, 10, 900, "2026-07-24T00:00:00Z", "2026-07-24T00:00:00Z");
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "alert.trigger", toolName, "ALERT_TRIGGER", "HIGH",
                false, false, true, false, false);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "alert.trigger", "alert_trigger_create",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, arguments,
                "session-1", "run-1", Map.of("projectId", "project-1"), Map.of());
    }
}
