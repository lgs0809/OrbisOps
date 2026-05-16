package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.AdminUserLoginRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService.LoginFailedException;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * 管理员和普通用户账号管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/admin-user")
public class AdminUserAdminController {

    private final AdminUserApplicationService adminUserApplicationService;

    public AdminUserAdminController(AdminUserApplicationService adminUserApplicationService) {
        this.adminUserApplicationService = adminUserApplicationService;
    }

    @PostMapping("/create")
    public Response<Boolean> createAdminUser(@RequestBody AdminUserRequestDTO request) {
        return handleBoolean("创建管理员用户失败", () -> adminUserApplicationService.create(request));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAdminUserById(@RequestBody AdminUserRequestDTO request) {
        return handleBoolean("根据ID更新管理员用户失败", () -> adminUserApplicationService.updateById(request));
    }

    @PutMapping("/update-by-user-id")
    public Response<Boolean> updateAdminUserByUserId(@RequestBody AdminUserRequestDTO request) {
        return handleBoolean("根据用户ID更新管理员用户失败", () -> adminUserApplicationService.updateByUserId(request));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAdminUserById(@PathVariable("id") Long id) {
        return handleBoolean("根据ID删除管理员用户失败", () -> adminUserApplicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-user-id/{userId}")
    public Response<Boolean> deleteAdminUserByUserId(@PathVariable("userId") String userId) {
        return handleBoolean("根据用户ID删除管理员用户失败", () -> adminUserApplicationService.deleteByUserId(userId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AdminUserResponseDTO> queryAdminUserById(@PathVariable("id") Long id) {
        return handleItem("根据ID查询管理员用户失败", () -> adminUserApplicationService.queryById(id));
    }

    @GetMapping("/query-by-user-id/{userId}")
    public Response<AdminUserResponseDTO> queryAdminUserByUserId(@PathVariable("userId") String userId) {
        return handleItem("根据用户ID查询管理员用户失败", () -> adminUserApplicationService.queryByUserId(userId));
    }

    @GetMapping("/query-by-username/{username}")
    public Response<AdminUserResponseDTO> queryAdminUserByUsername(@PathVariable("username") String username) {
        return handleItem("根据用户名查询管理员用户失败", () -> adminUserApplicationService.queryByUsername(username));
    }

    @GetMapping("/query-enabled")
    public Response<List<AdminUserResponseDTO>> queryEnabledAdminUsers() {
        return handleList("查询启用状态的管理员用户列表失败", adminUserApplicationService::queryEnabled);
    }

    @GetMapping("/query-by-status/{status}")
    public Response<List<AdminUserResponseDTO>> queryAdminUsersByStatus(@PathVariable("status") Integer status) {
        return handleList("根据状态查询管理员用户列表失败", () -> adminUserApplicationService.queryByStatus(status));
    }

    @PostMapping("/query-list")
    public Response<List<AdminUserResponseDTO>> queryAdminUserList(@RequestBody(required = false) AdminUserQueryRequestDTO request) {
        return handleList("根据条件查询管理员用户列表失败", () -> adminUserApplicationService.queryList(request));
    }

    @GetMapping("/query-all")
    public Response<List<AdminUserResponseDTO>> queryAllAdminUsers() {
        return handleList("查询所有管理员用户失败", adminUserApplicationService::queryAll);
    }

    @PostMapping("/login")
    public Response<AdminUserResponseDTO> loginAdminUser(@RequestBody AdminUserLoginRequestDTO request) {
        try {
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(adminUserApplicationService.login(request))
                    .build();
        } catch (IllegalArgumentException | LoginFailedException e) {
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("管理员用户登录失败", e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @PostMapping("/logout")
    public Response<Boolean> logout(HttpServletRequest request) {
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(adminUserApplicationService.logout(request.getHeader("Authorization")))
                .build();
    }

    @PostMapping("/validate-login")
    public Response<Boolean> validateAdminUserLogin(@RequestBody AdminUserLoginRequestDTO request) {
        try {
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(adminUserApplicationService.validateLogin(request))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .data(false)
                    .build();
        } catch (LoginFailedException e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.LOGIN_FAILED.getCode())
                    .info(e.getMessage())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("管理员用户登录校验失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    private Response<Boolean> handleBoolean(String errorMessage, Supplier<Boolean> action) {
        try {
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    private Response<AdminUserResponseDTO> handleItem(String errorMessage, Supplier<AdminUserResponseDTO> action) {
        try {
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    private Response<List<AdminUserResponseDTO>> handleList(String errorMessage, Supplier<List<AdminUserResponseDTO>> action) {
        try {
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }
}
