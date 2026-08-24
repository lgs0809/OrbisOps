package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.episode.*;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.GlobalExceptionHandler;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpsBackgroundModelRecoveryControllerTest {
    @Test void onlyAnAuthorizedAdminCanRequestRecoveryAndOriginalOwnerIsStillCheckedByTheService() throws Exception {
        var service=mock(BackgroundModelRecoveryService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsBackgroundModelRecoveryController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        String path="/api/v1/admin/ops/background-model-retries/EPISODE_TURN/task/recover";
        var admin=new AdminAuthService.AuthPrincipal("admin","admin-id","token",AdminAuthService.SCOPE_ADMIN,false);
        var user=new AdminAuthService.AuthPrincipal("user","user-id","token",AdminAuthService.SCOPE_USER,false);
        mvc.perform(post(path).param("projectId","p")).andExpect(status().isForbidden());
        mvc.perform(post(path).param("projectId","p").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,user)).andExpect(status().isForbidden());
        verifyNoInteractions(service,access);
        doThrow(new SecurityException("PROJECT_ACCESS_DENIED")).when(access).requireAccess("private","admin","admin-id",true);
        mvc.perform(post(path).param("projectId","private").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,admin)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.recover("p",BackgroundModelRecoveryPort.Family.EPISODE_TURN,"task","admin")).thenReturn(true);
        mvc.perform(post(path).param("projectId","p").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(access).requireAccess("p","admin","admin-id",true);
        verify(service).recover("p",BackgroundModelRecoveryPort.Family.EPISODE_TURN,"task","admin");
    }
}
