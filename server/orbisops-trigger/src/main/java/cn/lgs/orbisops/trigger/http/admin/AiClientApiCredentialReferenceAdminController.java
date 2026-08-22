package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.AiClientApiCredentialReferenceRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiCredentialReferenceResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.config.AiClientApiCredentialReferenceApplicationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoints for Provider credential environment references. */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-provider-references")
public class AiClientApiCredentialReferenceAdminController {

    private final AiClientApiCredentialReferenceApplicationService applicationService;

    public AiClientApiCredentialReferenceAdminController(
            AiClientApiCredentialReferenceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public Response<Boolean> create(@RequestBody AiClientApiCredentialReferenceRequestDTO request) {
        return change("创建 Provider 凭据引用配置失败", () -> applicationService.create(request));
    }

    @PutMapping
    public Response<Boolean> update(@RequestBody AiClientApiCredentialReferenceRequestDTO request) {
        return change("更新 Provider 凭据引用配置失败", () -> applicationService.update(request));
    }

    @GetMapping("/{apiId}")
    public Response<AiClientApiCredentialReferenceResponseDTO> query(@PathVariable String apiId) {
        if (!StringUtils.hasText(apiId)) {
            return Response.<AiClientApiCredentialReferenceResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("Provider API ID 不能为空")
                    .data(null)
                    .build();
        }
        try {
            AiClientApiCredentialReferenceResponseDTO result = applicationService.queryReference(apiId);
            return Response.<AiClientApiCredentialReferenceResponseDTO>builder()
                    .code(result == null ? ResponseCode.UN_ERROR.getCode() : ResponseCode.SUCCESS.getCode())
                    .info(result == null ? "未找到 Provider" : ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception error) {
            log.error("Unable to query Provider credential reference apiId={}", apiId, error);
            return Response.<AiClientApiCredentialReferenceResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询 Provider 凭据引用失败")
                    .data(null)
                    .build();
        }
    }

    private Response<Boolean> change(String fallback, ChangeAction action) {
        try {
            boolean changed = action.execute();
            return Response.<Boolean>builder()
                    .code(changed ? ResponseCode.SUCCESS.getCode() : ResponseCode.UN_ERROR.getCode())
                    .info(changed ? ResponseCode.SUCCESS.getInfo() : fallback)
                    .data(changed)
                    .build();
        } catch (Exception error) {
            log.error(fallback, error);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(fallback)
                    .data(false)
                    .build();
        }
    }

    @FunctionalInterface
    private interface ChangeAction {
        boolean execute();
    }
}
