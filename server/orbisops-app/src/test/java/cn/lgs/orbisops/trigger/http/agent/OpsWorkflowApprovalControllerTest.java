package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.worksession.OpsWorkflowApprovalWebApplicationService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkflowApprovalControllerTest {

    @Test
    void adminProjectReaderGetsSanitizedApprovalViewWithoutParticipantReadRequirement() {
        OpsWorkflowApprovalWebApplicationService approvals = mock(OpsWorkflowApprovalWebApplicationService.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsWorkflowApprovalController controller = new OpsWorkflowApprovalController(approvals, projectAccess);
        when(approvals.viewForProjectActor("run-1", "project-1", "user-1"))
                .thenReturn(Map.of("available", true, "status", "WAITING", "canDecide", false));

        var response = controller.approval("run-1", "project-1", request("admin", false));

        assertEquals("WAITING", response.getData().get("status"));
        assertEquals(false, response.getData().get("canDecide"));
        verify(projectAccess).requireAccess("project-1", "alice", "user-1", true);
        verify(approvals).viewForProjectActor("run-1", "project-1", "user-1");
        verify(approvals, never()).viewForActor(eq("run-1"), eq("project-1"), eq("user-1"));
    }

    @Test
    void ordinaryUserReadAlsoRequiresWorkSessionActorVisibility() {
        OpsWorkflowApprovalWebApplicationService approvals = mock(OpsWorkflowApprovalWebApplicationService.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsWorkflowApprovalController controller = new OpsWorkflowApprovalController(approvals, projectAccess);
        when(approvals.viewForActor("run-1", "project-1", "user-1"))
                .thenReturn(Map.of("available", true, "status", "WAITING"));

        controller.approval("run-1", "project-1", request("user", false));

        verify(projectAccess).requireAccess("project-1", "alice", "user-1", false);
        verify(approvals).viewForActor("run-1", "project-1", "user-1");
        verify(approvals, never()).view("run-1", "project-1");
    }

    @Test
    void decisionUsesAuthenticatedHumanActorAndNeverTrustsActorFromBody() {
        OpsWorkflowApprovalWebApplicationService approvals = mock(OpsWorkflowApprovalWebApplicationService.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsWorkflowApprovalController controller = new OpsWorkflowApprovalController(approvals, projectAccess);
        when(approvals.decide("run-1", "project-1", "user-1", "APPROVE", "workflow-approval-1"))
                .thenReturn(Map.of("status", "APPROVED"));

        var response = controller.decide(
                "run-1", "project-1", Map.of("decision", "APPROVE", "actor", "attacker", "approvalId", "workflow-approval-1"), request("user", false));

        assertEquals("APPROVED", response.getData().get("status"));
        verify(approvals).decide("run-1", "project-1", "user-1", "APPROVE", "workflow-approval-1");
    }

    @Test
    void serviceTokenCannotApproveHumanWorkflow() {
        OpsWorkflowApprovalWebApplicationService approvals = mock(OpsWorkflowApprovalWebApplicationService.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsWorkflowApprovalController controller = new OpsWorkflowApprovalController(approvals, projectAccess);

        SecurityException failure = assertThrows(SecurityException.class,
                () -> controller.decide(
                        "run-1", "project-1", Map.of("decision", "APPROVE"), request("admin", true)));

        assertEquals("WORKFLOW_APPROVAL_HUMAN_ACTOR_REQUIRED", failure.getMessage());
        verify(approvals, never()).decide(eq("run-1"), eq("project-1"), eq("user-1"), eq("APPROVE"), eq("workflow-approval-1"));
    }

    @Test
    void retryResumeUsesSameAuthenticatedHumanActor() {
        OpsWorkflowApprovalWebApplicationService approvals = mock(OpsWorkflowApprovalWebApplicationService.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsWorkflowApprovalController controller = new OpsWorkflowApprovalController(approvals, projectAccess);
        when(approvals.retryResume("run-1", "project-1", "user-1", "workflow-approval-1"))
                .thenReturn(Map.of("status", "APPROVED", "resumeRequired", true));

        controller.retryResume("run-1", "project-1", Map.of("approvalId", "workflow-approval-1"), request("admin", false));

        verify(approvals).retryResume("run-1", "project-1", "user-1", "workflow-approval-1");
    }

    private MockHttpServletRequest request(String scope, boolean serviceToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal("alice", "user-1", "subject-1", scope, serviceToken));
        return request;
    }
}
