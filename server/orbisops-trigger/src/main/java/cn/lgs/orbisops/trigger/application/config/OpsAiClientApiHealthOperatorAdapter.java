package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthOperatorPort;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Trigger adapter for the current authenticated admin operator. */
public final class OpsAiClientApiHealthOperatorAdapter implements AiClientApiHealthOperatorPort {

    @Override
    public String currentOperator() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object principal = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
            if (principal instanceof AdminAuthService.AuthPrincipal authPrincipal) {
                return StringUtils.hasText(authPrincipal.userId())
                        ? authPrincipal.userId()
                        : value(authPrincipal.username());
            }
        }
        return "";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
