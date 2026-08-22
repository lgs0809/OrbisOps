package cn.lgs.orbisops.trigger.http.admin.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import cn.lgs.orbisops.types.enums.ResponseCode;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.types.common.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Global hardening for all admin APIs.
 */
@Slf4j
@Configuration
public class AdminWebSecurityConfig implements WebMvcConfigurer {

    private static final String ADMIN_PATH_PATTERN = "/api/v1/admin/**";
    private static final String AGENT_PATH_PATTERN = "/api/v1/agent/**";
    private static final String USER_PATH_PATTERN = "/api/v1/user/**";
    private static final String LOGIN_PATH = "/api/v1/admin/admin-user/login";
    private static final String VALIDATE_LOGIN_PATH = "/api/v1/admin/admin-user/validate-login";
    private static final String SERVICE_TOKEN_HEADER = "X-Admin-Service-Token";
    public static final String AUTH_PRINCIPAL_ATTRIBUTE = AdminWebSecurityConfig.class.getName() + ".principal";

    private final String allowedOriginPatterns;
    private final AdminAuthSettings authSettings;
    private final AdminAuthService adminAuthService;
    private final OpsRateLimitService opsRateLimitService;

    public AdminWebSecurityConfig(@Value("${orbisops.allowed-origin-patterns:http://127.0.0.1:3000,http://localhost:3000}") String allowedOriginPatterns,
                                  AdminAuthSettings authSettings,
                                  AdminAuthService adminAuthService,
                                  OpsRateLimitService opsRateLimitService) {
        this.allowedOriginPatterns = allowedOriginPatterns;
        this.authSettings = authSettings;
        this.adminAuthService = adminAuthService;
        this.opsRateLimitService = opsRateLimitService;
    }

    @PostConstruct
    public void warnDefaultJwtSecret() {
        if (authSettings.hasWeakOrMissingJwtSecret()) {
            log.warn("orbisops.admin.auth.jwt-secret 未配置或强度不足；启用严格认证配置时应用将拒绝启动。");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(ADMIN_PATH_PATTERN)
                .allowedOriginPatterns(parseCsv(allowedOriginPatterns))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(TraceContext.HEADER_TRACE_ID)
                .maxAge(3600);
        registry.addMapping(AGENT_PATH_PATTERN)
                .allowedOriginPatterns(parseCsv(allowedOriginPatterns))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(TraceContext.HEADER_TRACE_ID)
                .maxAge(3600);
        registry.addMapping(USER_PATH_PATTERN)
                .allowedOriginPatterns(parseCsv(allowedOriginPatterns))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(TraceContext.HEADER_TRACE_ID)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(adminAuthService, opsRateLimitService))
                .addPathPatterns(ADMIN_PATH_PATTERN, AGENT_PATH_PATTERN, USER_PATH_PATTERN)
                .excludePathPatterns(LOGIN_PATH, VALIDATE_LOGIN_PATH);
    }

    private String[] parseCsv(String value) {
        String[] values = Arrays.stream((value == null ? "" : value).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        return values.length == 0 ? new String[]{"http://127.0.0.1:3000", "http://localhost:3000"} : values;
    }

    static class AdminAuthInterceptor implements HandlerInterceptor {

        private final AdminAuthService adminAuthService;
        private final OpsRateLimitService opsRateLimitService;

        AdminAuthInterceptor(AdminAuthService adminAuthService, OpsRateLimitService opsRateLimitService) {
            this.adminAuthService = adminAuthService;
            this.opsRateLimitService = opsRateLimitService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            if (adminAuthService.verifyServiceToken(request.getHeader(SERVICE_TOKEN_HEADER))) {
                request.setAttribute(AUTH_PRINCIPAL_ATTRIBUTE,
                        new AdminAuthService.AuthPrincipal("service-token", "service-token", "", AdminAuthService.SCOPE_ADMIN, true));
                return true;
            }

            String path = StringUtils.hasText(request.getServletPath()) ? request.getServletPath() : request.getRequestURI();
            if (path != null && (path.startsWith("/api/v1/agent/") || path.startsWith("/api/v1/user/"))) {
                var principal = adminAuthService.verifyAuthorization(
                        request.getHeader("Authorization"),
                        AdminAuthService.SCOPE_ADMIN,
                        AdminAuthService.SCOPE_USER);
                if (principal.isPresent()) {
                    request.setAttribute(AUTH_PRINCIPAL_ATTRIBUTE, principal.get());
                    return checkRateLimit(request, response, principal.get());
                }
            } else {
                var principal = adminAuthService.verifyAuthorization(
                        request.getHeader("Authorization"),
                        AdminAuthService.SCOPE_ADMIN,
                        AdminAuthService.SCOPE_USER);
                if (principal.isPresent()) {
                    if (!AdminAuthService.SCOPE_ADMIN.equals(principal.get().scope())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":\"" + ResponseCode.FORBIDDEN.getCode()
                                + "\",\"info\":\"Forbidden\"}");
                        return false;
                    }
                    request.setAttribute(AUTH_PRINCIPAL_ATTRIBUTE, principal.get());
                    return checkRateLimit(request, response, principal.get());
                }
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"401\",\"info\":\"Unauthorized\"}");
            return false;
        }

        private boolean checkRateLimit(HttpServletRequest request,
                                       HttpServletResponse response,
                                       AdminAuthService.AuthPrincipal principal) throws Exception {
            String path = StringUtils.hasText(request.getServletPath()) ? request.getServletPath() : request.getRequestURI();
            if (opsRateLimitService.tryAcquire(principal.username(), path)) {
                return true;
            }
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"" + ResponseCode.RATE_LIMITED.getCode() + "\",\"info\":\"" + ResponseCode.RATE_LIMITED.getInfo() + "\"}");
            return false;
        }
    }
}
