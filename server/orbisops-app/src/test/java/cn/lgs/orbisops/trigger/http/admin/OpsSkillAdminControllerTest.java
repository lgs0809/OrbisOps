package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogEditor;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogReader;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillAdminControllerTest {

    @Test
    void createGlobalSkillUsesAuthenticatedPrincipal() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        SkillManagementUseCase skillManagement = mock(SkillManagementUseCase.class);
        SkillCatalogQueryService queries = mock(SkillCatalogQueryService.class);
        OpsSkillAdminController controller = controller(definitions, audit, skillManagement, queries);
        when(skillManagement.createGlobalSkill(anyMap(), eq("alice")))
                .thenReturn(Map.of("skillId", "slow-sql", "createBy", "alice"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.createGlobalSkill(Map.of(
                "skillId", "slow-sql",
                "createBy", "forged-user"), request).getData();

        assertEquals("slow-sql", result.get("skillId"));
        verify(skillManagement).createGlobalSkill(anyMap(), eq("alice"));
    }

    @Test
    void updateProjectSkillUsesAuthenticatedPrincipal() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        SkillManagementUseCase skillManagement = mock(SkillManagementUseCase.class);
        SkillCatalogQueryService queries = mock(SkillCatalogQueryService.class);
        OpsSkillAdminController controller = controller(definitions, audit, skillManagement, queries);
        when(queries.getProjectSkill("project-1", "slow-sql"))
                .thenReturn(Map.of("skillId", "slow-sql", "createBy", "original-owner"));
        when(skillManagement.updateProjectSkill(
                eq("project-1"), eq("slow-sql"), anyMap(), eq("alice")))
                .thenReturn(Map.of("skillId", "slow-sql", "createBy", "original-owner"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.updateProjectSkill(
                "project-1",
                "slow-sql",
                Map.of("createBy", "forged-user"),
                request).getData();

        assertEquals("slow-sql", result.get("skillId"));
        verify(skillManagement).updateProjectSkill(
                eq("project-1"), eq("slow-sql"), anyMap(), eq("alice"));
    }

    @Test
    void rollbackGlobalSkillUsesAuthenticatedPrincipal() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        SkillManagementUseCase skillManagement = mock(SkillManagementUseCase.class);
        SkillCatalogQueryService queries = mock(SkillCatalogQueryService.class);
        OpsSkillAdminController controller = controller(definitions, audit, skillManagement, queries);
        when(queries.getGlobalSkill("slow-sql"))
                .thenReturn(Map.of("skillId", "slow-sql", "version", 3));
        when(skillManagement.rollbackGlobalVersion("slow-sql", 2, "alice"))
                .thenReturn(Map.of("skillId", "slow-sql", "version", 4));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.rollbackGlobalSkillVersion(
                "slow-sql", 2, request).getData();

        assertEquals(4, result.get("version"));
        verify(skillManagement).rollbackGlobalVersion("slow-sql", 2, "alice");
    }

    @SuppressWarnings("unchecked")
    private OpsSkillAdminController controller(
            OpsAgentDefinitionQueryGateway definitions,
            OpsConfigAuditService audit,
            SkillManagementUseCase skillManagement,
            SkillCatalogQueryService queries) {
        ObjectProvider<SkillCatalogReader> readers = mock(ObjectProvider.class);
        ObjectProvider<SkillCatalogEditor> editors = mock(ObjectProvider.class);
        ObjectProvider<SkillRuntimeToolProvider> runtime = mock(ObjectProvider.class);
        return new OpsSkillAdminController(
                readers, editors, runtime, definitions, audit, skillManagement, queries);
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
