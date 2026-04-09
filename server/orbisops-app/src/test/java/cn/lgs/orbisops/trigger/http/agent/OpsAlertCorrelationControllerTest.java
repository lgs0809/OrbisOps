package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.application.incident.AlertCorrelationApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.GlobalExceptionHandler;
import cn.lgs.orbisops.trigger.http.GlobalResponseStatusAdvice;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsAlertCorrelationControllerTest {
    final AlertCorrelationApplicationService service = mock(AlertCorrelationApplicationService.class);
    final OpsAlertCorrelationController controller = new OpsAlertCorrelationController(service, mock(AuthorizeProjectAccessUseCase.class));

    @ParameterizedTest @ValueSource(strings = {"bad-time", "2026-09-09 02:53:35", ""})
    void invalidTopologyTimeReturnsBadRequestWithoutChangingTopology(String time) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseStatusAdvice()).build();
        mvc.perform(put("/api/v1/admin/ops/alert-correlations/topology")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE, new AdminAuthService.AuthPrincipal("admin", "admin", "id", AdminAuthService.SCOPE_ADMIN, false))
                .contentType("application/json").content("""
                        {"projectId":"p","environment":"test","edges":[{"source":"a","target":"b","evidenceRef":"topology:1","observedAt":"%s","expiresAt":"2026-10-09T02:53:35Z"}]}
                        """.formatted(time))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void missingPrincipalCannotModifyTopology() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/api/v1/admin/ops/alert-correlations/topology").contentType("application/json")
                .content("{\"projectId\":\"p\",\"environment\":\"test\",\"edges\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
