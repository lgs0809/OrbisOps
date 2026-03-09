package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.AiClientModelQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientModelRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientModelResponseDTO;
import cn.lgs.orbisops.api.dto.AiClientModelSyncResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.config.AiClientModelApplicationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * AI 客户端模型配置管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-model")
public class AiClientModelAdminController {

    private final AiClientModelApplicationService aiClientModelApplicationService;

    public AiClientModelAdminController(AiClientModelApplicationService aiClientModelApplicationService) {
        this.aiClientModelApplicationService = aiClientModelApplicationService;
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientModel(@RequestBody AiClientModelRequestDTO request) {
        return handleBoolean("创建AI客户端模型配置失败", () -> aiClientModelApplicationService.create(request));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientModelById(@RequestBody AiClientModelRequestDTO request) {
        if (request == null || request.getId() == null) {
            return illegalParameter("ID不能为空");
        }
        return handleBoolean("根据ID更新AI客户端模型配置失败", () -> aiClientModelApplicationService.updateById(request));
    }

    @PutMapping("/update-by-model-id")
    public Response<Boolean> updateAiClientModelByModelId(@RequestBody AiClientModelRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getModelId())) {
            return illegalParameter("模型ID不能为空");
        }
        return handleBoolean("根据模型ID更新AI客户端模型配置失败", () -> aiClientModelApplicationService.updateByModelId(request));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientModelById(@PathVariable("id") Long id) {
        return handleBoolean("根据ID删除AI客户端模型配置失败", () -> aiClientModelApplicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-model-id/{modelId}")
    public Response<Boolean> deleteAiClientModelByModelId(@PathVariable("modelId") String modelId) {
        return handleBoolean("根据模型ID删除AI客户端模型配置失败", () -> aiClientModelApplicationService.deleteByModelId(modelId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientModelResponseDTO> queryAiClientModelById(@PathVariable("id") Long id) {
        return handleModel("根据ID查询AI客户端模型配置失败", () -> aiClientModelApplicationService.queryById(id));
    }

    @GetMapping("/query-by-model-id/{modelId}")
    public Response<AiClientModelResponseDTO> queryAiClientModelByModelId(@PathVariable("modelId") String modelId) {
        return handleModel("根据模型ID查询AI客户端模型配置失败", () -> aiClientModelApplicationService.queryByModelId(modelId));
    }

    @GetMapping("/query-by-api-id/{apiId}")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelsByApiId(@PathVariable("apiId") String apiId) {
        return handleList("根据API配置ID查询AI客户端模型配置列表失败", () -> aiClientModelApplicationService.queryByApiId(apiId));
    }

    @GetMapping("/query-by-model-type/{modelType}")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelsByModelType(@PathVariable("modelType") String modelType) {
        return handleList("根据模型类型查询AI客户端模型配置列表失败", () -> aiClientModelApplicationService.queryByModelType(modelType));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientModelResponseDTO>> queryEnabledAiClientModels() {
        return handleList("查询所有启用的AI客户端模型配置失败", aiClientModelApplicationService::queryEnabled);
    }

    @PostMapping("/query-list")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelList(@RequestBody(required = false) AiClientModelQueryRequestDTO request) {
        return handleList("根据条件查询AI客户端模型配置列表失败", () -> aiClientModelApplicationService.queryList(request));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientModelResponseDTO>> queryAllAiClientModels() {
        return handleList("查询所有AI客户端模型配置失败", aiClientModelApplicationService::queryAll);
    }

    @PostMapping("/sync-from-provider/{apiId}")
    public Response<AiClientModelSyncResponseDTO> syncFromProvider(@PathVariable("apiId") String apiId) {
        if (!StringUtils.hasText(apiId)) {
            return Response.<AiClientModelSyncResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("API ID不能为空")
                    .data(null)
                    .build();
        }
        try {
            AiClientModelSyncResponseDTO result = aiClientModelApplicationService.syncFromProvider(apiId);
            return Response.<AiClientModelSyncResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("同步 Provider 模型目录失败 apiId={}", apiId, e);
            return Response.<AiClientModelSyncResponseDTO>builder()
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

    private Response<AiClientModelResponseDTO> handleModel(String errorMessage, Supplier<AiClientModelResponseDTO> action) {
        try {
            AiClientModelResponseDTO model = action.get();
            if (model == null) {
                return Response.<AiClientModelResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端模型配置")
                        .data(null)
                        .build();
            }
            return Response.<AiClientModelResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(model)
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<AiClientModelResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    private Response<List<AiClientModelResponseDTO>> handleList(String errorMessage, Supplier<List<AiClientModelResponseDTO>> action) {
        try {
            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<List<AiClientModelResponseDTO>>builder()
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
