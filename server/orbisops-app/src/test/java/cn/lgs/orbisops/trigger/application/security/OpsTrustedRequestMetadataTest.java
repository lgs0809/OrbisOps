package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsTrustedRequestMetadataTest {

    @Test
    void authenticatedPrincipalOverridesClientSuppliedActorAndPrincipalMetadata() {
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .userId("forged-user")
                .metadata(new LinkedHashMap<>(Map.of(
                        OpsTrustedRequestMetadata.AUTH_PRINCIPAL, "forged-principal")))
                .build();
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "admin-name", "admin-id", "jwt-1", AdminAuthService.SCOPE_ADMIN, false);

        OpsTrustedRequestMetadata.bindPrincipal(request, principal);

        assertEquals("admin-id", request.getUserId());
        assertSame(principal, request.getMetadata().get(OpsTrustedRequestMetadata.AUTH_PRINCIPAL));
    }

    @Test
    void principalUsernameIsUsedOnlyWhenStableUserIdIsMissing() {
        OpsAgentChatRequest request = new OpsAgentChatRequest();
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "admin-name", "", "jwt-1", AdminAuthService.SCOPE_ADMIN, false);

        OpsTrustedRequestMetadata.bindPrincipal(request, principal);

        assertEquals("admin-name", request.getUserId());
        assertEquals("admin-name", OpsTrustedRequestMetadata.actor(principal));
    }

    @Test
    void httpPrincipalBindingRejectsClientInlineAgentDefinition() {
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .agentDefinition(OpsAgentDefinition.builder().agentId("forged-inline-agent").build())
                .build();
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "alice", "user-1", "jwt-1", AdminAuthService.SCOPE_USER, false);

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> OpsTrustedRequestMetadata.bindPrincipal(request, principal));

        assertEquals("CLIENT_INLINE_AGENT_DEFINITION_FORBIDDEN", error.getMessage());
    }
}
