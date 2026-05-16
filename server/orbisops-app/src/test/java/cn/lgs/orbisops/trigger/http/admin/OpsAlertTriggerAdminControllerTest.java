package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.application.alert.AlertTriggerProcessManager;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertTriggerAdminControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void createRuleUsesAuthenticatedPrincipalAndMasksSecret() {
        AlertRuleManagementApplicationService alertRules = mock(AlertRuleManagementApplicationService.class);
        AlertEventApplicationService alertEvents = mock(AlertEventApplicationService.class);
        AlertTriggerProcessManager<OpsAlertWebhookResult> alertProcess = mock(AlertTriggerProcessManager.class);
        OpsAlertTriggerAdminController controller = new OpsAlertTriggerAdminController(
                alertRules, alertEvents, alertProcess, new OpsAlertRuleMapper(), new OpsAlertEventMapper());
        when(alertRules.save(any(AlertRuleCandidate.class), eq("alice"))).thenReturn(definition());

        OpsAlertTriggerRule result = controller.createAlertTriggerRule(
                OpsAlertTriggerRule.builder()
                        .ruleName("Payment Alert")
                        .projectId("project-1")
                        .agentDefinitionId("agent-1")
                        .build(),
                authenticatedRequest("alice")).getData();

        assertEquals(7L, result.getId());
        assertEquals("******", result.getWebhookSecret());
        verify(alertRules).save(any(AlertRuleCandidate.class), eq("alice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteRuleUsesAuthenticatedPrincipal() {
        AlertRuleManagementApplicationService alertRules = mock(AlertRuleManagementApplicationService.class);
        AlertEventApplicationService alertEvents = mock(AlertEventApplicationService.class);
        AlertTriggerProcessManager<OpsAlertWebhookResult> alertProcess = mock(AlertTriggerProcessManager.class);
        OpsAlertTriggerAdminController controller = new OpsAlertTriggerAdminController(
                alertRules, alertEvents, alertProcess, new OpsAlertRuleMapper(), new OpsAlertEventMapper());
        when(alertRules.delete(7L, "alice")).thenReturn(true);

        Boolean result = controller.deleteAlertTriggerRule(7L, authenticatedRequest("alice")).getData();

        assertEquals(true, result);
        verify(alertRules).delete(7L, "alice");
    }

    private AlertRuleDefinition definition() {
        return new AlertRuleDefinition(
                7L, "Payment Alert", 1, "ALERTMANAGER", "", "", "", Map.of(),
                "", "", "secret", "project-1", "agent-1", "LATEST_PUBLISHED", 4,
                "agent-v4", "", 30, "5m", true, false, 5, 120, 20, 300,
                "created", "updated");
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
