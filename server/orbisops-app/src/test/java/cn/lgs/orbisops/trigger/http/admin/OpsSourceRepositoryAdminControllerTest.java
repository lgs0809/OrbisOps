package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsProjectServiceDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryRequestDTO;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.source.OpsProjectServiceCatalogService;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSourceRepositoryAdminControllerTest {

    @Test
    void forwardsAuthenticatedActorForRepositoryAndServiceWrites() {
        OpsSourceRepositoryService repositories = mock(OpsSourceRepositoryService.class);
        OpsProjectServiceCatalogService services = mock(OpsProjectServiceCatalogService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE))
                .thenReturn(new AdminAuthService.AuthPrincipal("admin", "user-1", "jwt-1", "ADMIN", false));
        when(repositories.registerRepository(any(), eq("admin"))).thenReturn(
                OpsSourceRepositoryDTO.builder().repositoryId("repo-1").build());
        when(services.upsert(any(), eq("admin"))).thenReturn(
                OpsProjectServiceDTO.builder().serviceId("service-1").build());
        OpsSourceRepositoryAdminController controller =
                new OpsSourceRepositoryAdminController(repositories, services);

        assertEquals("repo-1", controller.registerRepository(
                OpsSourceRepositoryRequestDTO.builder().projectId("project-1").build(), request)
                .getData().getRepositoryId());
        assertEquals("service-1", controller.upsertService(
                OpsProjectServiceRequestDTO.builder().projectId("project-1").build(), request)
                .getData().getServiceId());
        verify(repositories).registerRepository(any(), eq("admin"));
        verify(services).upsert(any(), eq("admin"));
    }

    @Test
    void preservesNullResolveAndFailureEnvelopeContracts() {
        OpsSourceRepositoryService repositories = mock(OpsSourceRepositoryService.class);
        OpsProjectServiceCatalogService services = mock(OpsProjectServiceCatalogService.class);
        when(repositories.resolveDeployment("project-1", "prod", "service-1"))
                .thenReturn(java.util.Optional.empty());
        when(repositories.listRepositories("project-1"))
                .thenThrow(new IllegalStateException("store down"));
        OpsSourceRepositoryAdminController controller =
                new OpsSourceRepositoryAdminController(repositories, services);

        assertEquals(null, controller.resolveDeployment("project-1", "prod", "service-1").getData());
        var failure = controller.listRepositories("project-1");
        assertEquals(null, failure.getData());
        org.junit.jupiter.api.Assertions.assertTrue(failure.getInfo().contains("store down"));
    }
}
