package cn.lgs.orbisops.trigger.http;

import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.security.AdminCredentialLifecycleUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/account")
public class UserAccountController {
    private final AdminCredentialLifecycleUseCase credentials;
    private final AdminUserCatalogPort catalog;
    public UserAccountController(AdminCredentialLifecycleUseCase credentials, AdminUserCatalogPort catalog) {
        this.credentials = credentials;
        this.catalog = catalog;
    }
    @GetMapping
    public Response<AdminUserResponseDTO> profile(HttpServletRequest request) {
        var account = catalog.findByUserId(principal(request).userId());
        return Response.<AdminUserResponseDTO>builder().code("0000").info("成功")
                .data(AdminUserApplicationService.toResponse(account, null)).build();
    }
    @PostMapping("/password")
    public Response<AdminUserResponseDTO> changePassword(@RequestBody PasswordChange body, HttpServletRequest request) {
        try {
            var result = credentials.changePassword(principal(request).userId(), body.currentPassword(), body.newPassword());
            return Response.<AdminUserResponseDTO>builder().code("0000").info("密码已更新")
                    .data(AdminUserApplicationService.toResponse(result.account(), result.token())).build();
        } catch (IllegalArgumentException error) {
            return Response.<AdminUserResponseDTO>builder().code("A0001").info(error.getMessage()).build();
        }
    }
    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        var value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof AdminAuthService.AuthPrincipal p) || p.serviceToken())
            throw new IllegalArgumentException("请使用个人账号登录");
        return p;
    }
    public record PasswordChange(String currentPassword, String newPassword) {
        @Override public String toString() { return "PasswordChange[redacted]"; }
    }
}
