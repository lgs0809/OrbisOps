package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.security.AdminCredentialLifecycleUseCase;
import org.springframework.web.bind.annotation.*;

/** Namespace retains administrator authentication; no password or hash is returned. */
@RestController
@RequestMapping("/api/v1/admin/admin-user")
public class AdminCredentialResetController {
    private final AdminCredentialLifecycleUseCase credentials;
    public AdminCredentialResetController(AdminCredentialLifecycleUseCase credentials) { this.credentials = credentials; }
    @PostMapping("/{userId}/reset-password")
    public Response<Boolean> reset(@PathVariable String userId) {
        try {
            return Response.<Boolean>builder().code("0000").info("已恢复初始密码，下次登录须修改")
                    .data(credentials.resetPassword(userId)).build();
        } catch (IllegalArgumentException error) {
            return Response.<Boolean>builder().code("A0001").info(error.getMessage()).data(false).build();
        }
    }
}
