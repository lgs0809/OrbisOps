package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientModelQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientModelRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientModelResponseDTO;
import cn.lgs.orbisops.api.dto.AiClientModelSyncResponseDTO;
import cn.lgs.orbisops.application.config.AiClientModelCatalogQuery;
import cn.lgs.orbisops.application.config.AiClientModelCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncResult;
import cn.lgs.orbisops.application.config.AiClientModelSyncUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 客户端模型配置用例服务。
 */
@Service
public class AiClientModelApplicationService {

    private final AiClientModelCatalogUseCase catalogUseCase;
    private final AiClientModelSyncUseCase syncUseCase;

    public AiClientModelApplicationService(
            AiClientModelCatalogUseCase catalogUseCase,
            AiClientModelSyncUseCase syncUseCase) {
        if (catalogUseCase == null || syncUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_SERVICE_DEPENDENCY_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
        this.syncUseCase = syncUseCase;
    }

    public boolean create(AiClientModelRequestDTO request) {
        return catalogUseCase.create(toDefinition(request));
    }

    public boolean updateById(AiClientModelRequestDTO request) {
        return catalogUseCase.updateById(toDefinition(request));
    }

    public boolean updateByModelId(AiClientModelRequestDTO request) {
        return catalogUseCase.updateByModelId(toDefinition(request));
    }

    public boolean deleteById(Long id) {
        return catalogUseCase.deleteById(id);
    }

    public boolean deleteByModelId(String modelId) {
        return catalogUseCase.deleteByModelId(modelId);
    }

    public AiClientModelResponseDTO queryById(Long id) {
        return toResponse(catalogUseCase.findById(id));
    }

    public AiClientModelResponseDTO queryByModelId(String modelId) {
        return toResponse(catalogUseCase.findByModelId(modelId));
    }

    public List<AiClientModelResponseDTO> queryByApiId(String apiId) {
        return toResponses(catalogUseCase.findByApiId(apiId));
    }

    public List<AiClientModelResponseDTO> queryByModelType(String modelType) {
        return toResponses(catalogUseCase.findByModelType(modelType));
    }

    public List<AiClientModelResponseDTO> queryEnabled() {
        return toResponses(catalogUseCase.listEnabled());
    }

    public List<AiClientModelResponseDTO> queryAll() {
        return toResponses(catalogUseCase.listAll());
    }

    public List<AiClientModelResponseDTO> queryList(AiClientModelQueryRequestDTO request) {
        AiClientModelCatalogQuery query = request == null
                ? AiClientModelCatalogQuery.all()
                : new AiClientModelCatalogQuery(
                        request.getModelId(),
                        request.getApiId(),
                        request.getModelType(),
                        request.getStatus());
        return toResponses(catalogUseCase.query(query));
    }

    public AiClientModelSyncResponseDTO syncFromProvider(String apiId) {
        return toSyncResponse(syncUseCase.sync(apiId));
    }

    private AiClientModelDefinition toDefinition(AiClientModelRequestDTO request) {
        return new AiClientModelDefinition(
                request == null ? null : request.getId(),
                request == null ? null : request.getModelId(),
                request == null ? null : request.getApiId(),
                request == null ? null : request.getModelName(),
                request == null ? null : request.getModelType(),
                request == null ? null : request.getModelUsage(),
                request == null ? null : request.getDescription(),
                request == null ? null : request.getStatus(),
                null,
                null);
    }

    private List<AiClientModelResponseDTO> toResponses(List<AiClientModelDefinition> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        return models.stream().map(this::toResponse).toList();
    }

    private AiClientModelResponseDTO toResponse(AiClientModelDefinition model) {
        if (model == null) {
            return null;
        }
        return AiClientModelResponseDTO.builder()
                .id(model.id())
                .modelId(model.modelId())
                .apiId(model.apiId())
                .modelName(model.modelName())
                .modelType(model.modelType())
                .modelUsage(model.modelUsage())
                .description(model.description())
                .status(model.status())
                .createTime(model.createTime())
                .updateTime(model.updateTime())
                .build();
    }

    private AiClientModelSyncResponseDTO toSyncResponse(AiClientModelSyncResult result) {
        if (result == null) {
            return null;
        }
        return AiClientModelSyncResponseDTO.builder()
                .apiId(result.apiId())
                .endpoint(result.endpoint())
                .httpStatus(result.httpStatus())
                .fetchedCount(result.fetchedCount())
                .createdCount(result.createdCount())
                .updatedCount(result.updatedCount())
                .skippedCount(result.skippedCount())
                .modelIds(result.modelIds())
                .errorMessage(result.errorMessage())
                .syncedAt(result.syncedAt())
                .build();
    }
}
