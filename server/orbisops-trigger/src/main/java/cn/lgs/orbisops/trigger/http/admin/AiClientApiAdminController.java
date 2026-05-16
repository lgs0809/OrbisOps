package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.AiClientApiHealthCheckResponseDTO;
import cn.lgs.orbisops.api.dto.AiClientApiQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.config.AiClientApiApplicationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * AI 客户端 API 配置管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-api")
public class AiClientApiAdminController {

    private final AiClientApiApplicationService aiClientApiApplicationService;

    public AiClientApiAdminController(AiClientApiApplicationService aiClientApiApplicationService) {
        this.aiClientApiApplicationService = aiClientApiApplicationService;
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientApi(@RequestBody AiClientApiRequestDTO request) {
        return handleBoolean("创建AI客户端API配置失败", () -> aiClientApiApplicationService.create(request));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientApiById(@RequestBody AiClientApiRequestDTO request) {
        if (request == null || request.getId() == null) {
            return illegalParameter("ID不能为空");
        }
        return handleBoolean("根据ID更新AI客户端API配置失败", () -> aiClientApiApplicationService.updateById(request));
    }

    @PutMapping("/update-by-api-id")
    public Response<Boolean> updateAiClientApiByApiId(@RequestBody AiClientApiRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getApiId())) {
            return illegalParameter("API ID不能为空");
        }
        return handleBoolean("根据API ID更新AI客户端API配置失败", () -> aiClientApiApplicationService.updateByApiId(request));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientApiById(@PathVariable("id") Long id) {
        return handleBoolean("根据ID删除AI客户端API配置失败", () -> aiClientApiApplicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-api-id/{apiId}")
    public Response<Boolean> deleteAiClientApiByApiId(@PathVariable("apiId") String apiId) {
        return handleBoolean("根据API ID删除AI客户端API配置失败", () -> aiClientApiApplicationService.deleteByApiId(apiId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientApiResponseDTO> queryAiClientApiById(@PathVariable("id") Long id) {
        return handleItem("根据ID查询AI客户端API配置失败", () -> aiClientApiApplicationService.queryById(id));
    }

    @GetMapping("/query-by-api-id/{apiId}")
    public Response<AiClientApiResponseDTO> queryAiClientApiByApiId(@PathVariable("apiId") String apiId) {
        return handleItem("根据API ID查询AI客户端API配置失败", () -> aiClientApiApplicationService.queryByApiId(apiId));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientApiResponseDTO>> queryEnabledAiClientApis() {
        return handleList("查询所有启用的AI客户端API配置失败", aiClientApiApplicationService::queryEnabled);
    }

    @PostMapping("/query-list")
    public Response<List<AiClientApiResponseDTO>> queryAiClientApiList(@RequestBody(required = false) AiClientApiQueryRequestDTO request) {
        return handleList("分页查询AI客户端API配置列表失败", () -> aiClientApiApplicationService.queryList(request));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientApiResponseDTO>> queryAllAiClientApis() {
        return handleList("查询所有AI客户端API配置失败", aiClientApiApplicationService::queryAll);
    }

    @PostMapping("/health-check/{apiId}")
    public Response<AiClientApiHealthCheckResponseDTO> healthCheck(@PathVariable("apiId") String apiId) {
        if (!StringUtils.hasText(apiId)) {
            return Response.<AiClientApiHealthCheckResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("API ID不能为空")
                    .data(null)
                    .build();
        }
        try {
            AiClientApiHealthCheckResponseDTO result = aiClientApiApplicationService.healthCheck(apiId);
            boolean success = result != null && "SUCCESS".equalsIgnoreCase(result.getStatus());
            return Response.<AiClientApiHealthCheckResponseDTO>builder()
                    .code(success ? ResponseCode.SUCCESS.getCode() : ResponseCode.UN_ERROR.getCode())
                    .info(success ? ResponseCode.SUCCESS.getInfo() : result == null ? "Provider 健康检测失败" : result.getErrorMessage())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("Provider 健康检测失败 apiId={}", apiId, e);
            return Response.<AiClientApiHealthCheckResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .data(null)
                    .build();
        }
    }

    private Response<Boolean> handleBoolean(String errorMessage, Supplier<Boolean> action) {
        try {
            boolean changed = Boolean.TRUE.equals(action.get());
            return Response.<Boolean>builder()
                    .code(changed ? ResponseCode.SUCCESS.getCode() : ResponseCode.UN_ERROR.getCode())
                    .info(changed ? ResponseCode.SUCCESS.getInfo() : errorMessage)
                    .data(changed)
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

    private Response<AiClientApiResponseDTO> handleItem(String errorMessage, Supplier<AiClientApiResponseDTO> action) {
        try {
            AiClientApiResponseDTO item = action.get();
            if (item == null) {
                return Response.<AiClientApiResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端API配置")
                        .data(null)
                        .build();
            }
            return Response.<AiClientApiResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(item)
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<AiClientApiResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    private Response<List<AiClientApiResponseDTO>> handleList(String errorMessage, Supplier<List<AiClientApiResponseDTO>> action) {
        try {
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    private Response<Boolean> illegalParameter(String message) {
        return Response.<Boolean>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .data(false)
                .build();
    }
}
