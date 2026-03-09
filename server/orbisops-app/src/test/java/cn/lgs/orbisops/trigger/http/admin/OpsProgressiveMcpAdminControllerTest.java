package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.mcp.DiscoverMcpToolsProcessManager;
import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpPolicyQueryService;
import cn.lgs.orbisops.application.mcp.McpRuntimeHistoryQueryService;
import cn.lgs.orbisops.application.mcp.McpToolSnapshotQueryService;
import cn.lgs.orbisops.application.mcp.ReviewMcpPolicyUseCase;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpAuthoritativeSchemaHydrationService;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpDiscoverySelectionMapper;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpToolPolicyCommandMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProgressiveMcpAdminControllerTest {

    @Test
    void upsertPolicyUsesAuthenticatedPrincipal() {
        DiscoverMcpToolsProcessManager discovery = mock(DiscoverMcpToolsProcessManager.class);
        McpRuntimeHistoryQueryService history = mock(McpRuntimeHistoryQueryService.class);
        McpToolSnapshotQueryService snapshots = mock(McpToolSnapshotQueryService.class);
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        ReviewMcpPolicyUseCase review = mock(ReviewMcpPolicyUseCase.class);
        OpsMcpAuthoritativeSchemaHydrationService schemaHydration =
                mock(OpsMcpAuthoritativeSchemaHydrationService.class);
        OpsProgressiveMcpAdminController controller =
                new OpsProgressiveMcpAdminController(
                        discovery, history, snapshots, policies, review, schemaHydration,
                        new OpsMcpDiscoverySelectionMapper(),
                        new OpsMcpToolPolicyCommandMapper());
        McpToolPolicy policy = mock(McpToolPolicy.class);
        Map<String, Object> policyView = Map.of("policyId", "policy-1");
        when(review.upsert(any())).thenReturn(policy);
        when(policies.view(policy)).thenReturn(policyView);
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.upsertToolPolicy(
                "project-1",
                Map.of("toolName", "query_db"),
                request).getData();

        assertEquals("policy-1", result.get("policyId"));
        ArgumentCaptor<McpCommands.PolicyMutation> command =
                ArgumentCaptor.forClass(McpCommands.PolicyMutation.class);
        verify(review).upsert(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("alice", command.getValue().actor());
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
