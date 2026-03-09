package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiHealthCheckResponseDTO;
import cn.lgs.orbisops.api.dto.AiClientApiQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogQuery;
import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckUseCase;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Legacy admin DTO facade over typed API provider catalog and health-check use cases. */
@Service
public class AiClientApiApplicationService {

    private static final String MASK = "******";

    private final AiClientApiCatalogUseCase catalogUseCase;
    private final AiClientApiHealthCheckUseCase healthCheckUseCase;

    public AiClientApiApplicationService(
            AiClientApiCatalogUseCase catalogUseCase,
            AiClientApiHealthCheckUseCase healthCheckUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_USE_CASE_REQUIRED");
        }
        if (healthCheckUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_HEALTH_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
        this.healthCheckUseCase = healthCheckUseCase;
    }

    public boolean create(AiClientApiRequestDTO request) {
        return catalogUseCase.create(toDefinition(request));
    }

    public boolean updateById(AiClientApiRequestDTO request) {
        return catalogUseCase.updateById(toDefinition(request));
    }

    public boolean updateByApiId(AiClientApiRequestDTO request) {
        return catalogUseCase.updateByApiId(toDefinition(request));
    }

    public boolean deleteById(Long id) {
        return catalogUseCase.deleteById(id);
    }

    public boolean deleteByApiId(String apiId) {
        return catalogUseCase.deleteByApiId(apiId);
    }

    public AiClientApiResponseDTO queryById(Long id) {
        return toResponse(catalogUseCase.findById(id));
    }

    public AiClientApiResponseDTO queryByApiId(String apiId) {
        return toResponse(catalogUseCase.findByApiId(apiId));
    }

    public List<AiClientApiResponseDTO> queryEnabled() {
        return toResponses(catalogUseCase.listEnabled());
    }

    public List<AiClientApiResponseDTO> queryAll() {
        return toResponses(catalogUseCase.listAll());
    }

    public List<AiClientApiResponseDTO> queryList(AiClientApiQueryRequestDTO request) {
        AiClientApiCatalogQuery query = request == null
                ? AiClientApiCatalogQuery.all()
                : new AiClientApiCatalogQuery(
                        request.getApiId(),
                        request.getBaseUrl(),
                        request.getStatus(),
                        request.getPageNum() == null ? 1 : request.getPageNum(),
                        request.getPageSize() == null ? 10 : request.getPageSize());
        return toResponses(catalogUseCase.query(query));
    }

    public AiClientApiHealthCheckResponseDTO healthCheck(String apiId) {
        return toHealthResponse(healthCheckUseCase.check(apiId));
    }

    private AiClientApiDefinition toDefinition(AiClientApiRequestDTO request) {
        return new AiClientApiDefinition(
                request == null ? null : request.getId(),
                request == null ? null : request.getApiId(),
                request == null ? null : request.getProviderName(),
                request == null ? null : request.getProviderType(),
                request == null ? null : request.getBaseUrl(),
                request == null ? null : request.getApiKey(),
                request == null ? null : request.getCompletionsPath(),
                request == null ? null : request.getEmbeddingsPath(),
                request == null ? null : request.getStatus(),
                null,
                null);
    }

    private List<AiClientApiResponseDTO> toResponses(List<AiClientApiDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        return definitions.stream().map(this::toResponse).toList();
    }

    private AiClientApiResponseDTO toResponse(AiClientApiDefinition definition) {
        if (definition == null) {
            return null;
        }
        return AiClientApiResponseDTO.builder()
                .id(definition.id())
                .apiId(definition.apiId())
                .providerName(definition.providerName())
                .providerType(definition.providerType())
                .baseUrl(definition.baseUrl())
                .apiKey(StringUtils.hasText(definition.apiKey()) ? MASK : definition.apiKey())
                .completionsPath(definition.completionsPath())
                .embeddingsPath(definition.embeddingsPath())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }

    private AiClientApiHealthCheckResponseDTO toHealthResponse(
            AiClientApiHealthCheckResult result) {
        if (result == null) {
            return null;
        }
        return AiClientApiHealthCheckResponseDTO.builder()
                .apiId(result.apiId())
                .testType(result.testType())
                .endpoint(result.endpoint())
                .status(result.status())
                .httpStatus(result.httpStatus())
                .latencyMs(result.latencyMs())
                .errorMessage(result.errorMessage())
                .testedBy(result.testedBy())
                .testTime(result.testTime())
                .checkedAt(result.checkedAt())
                .build();
    }
}
