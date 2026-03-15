package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;

/** Server-owned request metadata keys. Clients must never be trusted to author these values. */
public final class OpsTrustedRequestMetadata {

    public static final String AUTH_PRINCIPAL = "_trustedAuthPrincipal";

    private OpsTrustedRequestMetadata() {
    }

    /**
     * Binds the authenticated principal as the authoritative runtime actor.
     * Any client supplied userId or trusted-principal-shaped metadata is overwritten.
     */
    public static void bindPrincipal(
            OpsAgentChatRequest request,
            AdminAuthService.AuthPrincipal principal) {
        if (request == null) return;
        if (request.getAgentDefinition() != null) {
            throw new SecurityException("CLIENT_INLINE_AGENT_DEFINITION_FORBIDDEN");
        }
        if (principal == null) return;
        request.setUserId(actor(principal));
        if (request.getMetadata() == null) request.setMetadata(new LinkedHashMap<>());
        request.getMetadata().put(AUTH_PRINCIPAL, principal);
    }

    public static String actor(AdminAuthService.AuthPrincipal principal) {
        if (principal == null) return "";
        return StringUtils.hasText(principal.userId())
                ? principal.userId().trim()
                : principal.username() == null ? "" : principal.username().trim();
    }
}
