package cn.lgs.orbisops.trigger.http;

import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.api.dto.FirstTimeSetupRequestDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService.LoginFailedException;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public only until the first platform administrator has been created. */
@RestController
@RequestMapping("/api/v1/setup")
public class FirstTimeSetupController {

    private final AdminUserApplicationService adminUserApplicationService;

    public FirstTimeSetupController(AdminUserApplicationService adminUserApplicationService) {
        this.adminUserApplicationService = adminUserApplicationService;
    }

    @GetMapping("/status")
    public Response<Boolean> status() {
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(adminUserApplicationService.firstTimeSetupRequired())
                .build();
    }

    @PostMapping
    public Response<AdminUserResponseDTO> setup(@RequestBody FirstTimeSetupRequestDTO request) {
        try {
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(adminUserApplicationService.setupFirstAdministrator(request))
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | LoginFailedException error) {
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(error.getMessage())
                    .data(null)
                    .build();
        }
    }
}
