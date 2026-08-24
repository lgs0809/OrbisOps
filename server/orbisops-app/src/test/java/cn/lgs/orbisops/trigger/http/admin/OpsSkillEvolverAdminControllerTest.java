package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.GlobalExceptionHandler;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsSkillEvolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsSkillEvolverAdminControllerTest {
    private static final String PATH="/api/v1/admin/ops/skill-evolver";
    private static final AdminAuthService.AuthPrincipal ADMIN=
            new AdminAuthService.AuthPrincipal("admin","admin-id","fixture-token",AdminAuthService.SCOPE_ADMIN,false);

    @Test void scopedQueriesAuthorizeAndDelegateBeforeReadingJobsOrPatches() throws Exception {
        var service=mock(OpsSkillEvolutionService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsSkillEvolverAdminController(service,access))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get(PATH+"/jobs").param("projectId","project").param("status","FAILED")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,ADMIN)).andExpect(status().isOk());
        mvc.perform(get(PATH+"/patches").param("projectId","project").param("jobId","job").param("decision","NO_CHANGE")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,ADMIN)).andExpect(status().isOk());
        verify(access,times(2)).requireAccess("project","admin","admin-id",true);
        verify(service).listJobs("project","FAILED",100);
        verify(service).listPatches("project","job","NO_CHANGE",100);
    }

    @Test void globalAdminQueriesRemainCompatibleWithNullScope() throws Exception {
        var service=mock(OpsSkillEvolutionService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsSkillEvolverAdminController(service,access))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get(PATH+"/jobs").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,ADMIN)).andExpect(status().isOk());
        mvc.perform(get(PATH+"/patches").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,ADMIN)).andExpect(status().isOk());
        verifyNoInteractions(access);
        verify(service).listJobs(null,null,100);verify(service).listPatches(null,null,null,100);
    }

    @Test void absentUserOrDeniedProjectNeverReadsTheGlobalQueue() throws Exception {
        var service=mock(OpsSkillEvolutionService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsSkillEvolverAdminController(service,access))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user=new AdminAuthService.AuthPrincipal("user","user-id","fixture-token",AdminAuthService.SCOPE_USER,false);
        doThrow(new SecurityException("PROJECT_ACCESS_DENIED")).when(access).requireAccess("private","admin","admin-id",true);
        for(String route:java.util.List.of("/jobs","/patches")) {
            mvc.perform(get(PATH+route)).andExpect(status().isForbidden());
            mvc.perform(get(PATH+route).param("projectId","project")
                    .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,user)).andExpect(status().isForbidden());
            mvc.perform(get(PATH+route).param("projectId","private")
                    .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,ADMIN)).andExpect(status().isForbidden());
        }
        verify(access,times(2)).requireAccess("private","admin","admin-id",true);
        verifyNoInteractions(service);
    }
}
