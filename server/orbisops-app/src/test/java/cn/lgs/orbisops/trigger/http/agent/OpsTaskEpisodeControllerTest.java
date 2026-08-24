package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.application.episode.TaskEpisodeApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.GlobalExceptionHandler;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsTaskEpisodeControllerTest {
    @Test void projectAccessAndSessionOwnerBothRemainRequired() throws Exception {
        var service=mock(TaskEpisodeApplicationService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var controller=new OpsTaskEpisodeController(service,access);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/user/ops/task-episodes").param("projectId","p").param("sessionId","s")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        var principal=new AdminAuthService.AuthPrincipal("viewer","uid","jwt-fixture","USER",false);
        when(service.view("p","s","uid",false,100)).thenThrow(new SecurityException("EPISODE_SESSION_ACCESS_DENIED"));
        mvc.perform(get("/api/v1/user/ops/task-episodes").param("projectId","p").param("sessionId","s")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,principal)).andExpect(status().isForbidden());
        verify(access).requireAccess("p","viewer","uid",false);
    }
    @Test void projectMemberCannotScheduleAdministrativeSweep() throws Exception {
        var service=mock(TaskEpisodeApplicationService.class);var controller=new OpsTaskEpisodeController(service,mock(AuthorizeProjectAccessUseCase.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/admin/ops/task-episodes/sweep").param("projectId","p")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,new AdminAuthService.AuthPrincipal("viewer","viewer","uid","USER",false)))
                .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}
