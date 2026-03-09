package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.api.dto.AiClientRagOrderQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientRagOrderRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientRagOrderResponseDTO;
import cn.lgs.orbisops.application.rag.RagOrderCatalogQuery;
import cn.lgs.orbisops.application.rag.RagOrderCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

/** Legacy RAG order DTO compatibility facade over the typed catalog use case. */
@Service
public class AiClientRagOrderApplicationService {

    private final RagOrderCatalogUseCase ragOrderCatalog;

    public AiClientRagOrderApplicationService(RagOrderCatalogUseCase ragOrderCatalog) {
        if (ragOrderCatalog == null) {
            throw new IllegalArgumentException("RAG_ORDER_CATALOG_USE_CASE_REQUIRED");
        }
        this.ragOrderCatalog = ragOrderCatalog;
    }

    public boolean create(AiClientRagOrderRequestDTO request) {
        return ragOrderCatalog.create(toDefinition(request));
    }

    public boolean updateById(AiClientRagOrderRequestDTO request) {
        return ragOrderCatalog.updateById(toDefinition(request));
    }

    public boolean updateByRagId(AiClientRagOrderRequestDTO request) {
        return ragOrderCatalog.updateByRagId(toDefinition(request));
    }

    public boolean deleteById(Long id) {
        return ragOrderCatalog.deleteById(id);
    }

    public boolean deleteByRagId(String ragId) {
        return ragOrderCatalog.deleteByRagId(ragId);
    }

    public AiClientRagOrderResponseDTO queryById(Long id) {
        return toResponse(ragOrderCatalog.queryById(id));
    }

    public AiClientRagOrderResponseDTO queryByRagId(String ragId) {
        return toResponse(ragOrderCatalog.queryByRagId(ragId));
    }

    public List<AiClientRagOrderResponseDTO> queryEnabled() {
        return toResponses(ragOrderCatalog.queryEnabled());
    }

    public List<AiClientRagOrderResponseDTO> queryByKnowledgeTag(String knowledgeTag) {
        return toResponses(ragOrderCatalog.queryByKnowledgeTag(knowledgeTag));
    }

    public List<AiClientRagOrderResponseDTO> queryByStatus(Integer status) {
        return toResponses(ragOrderCatalog.queryByStatus(status));
    }

    public List<AiClientRagOrderResponseDTO> queryAll() {
        return toResponses(ragOrderCatalog.queryAll());
    }

    public List<AiClientRagOrderResponseDTO> queryList(AiClientRagOrderQueryRequestDTO request) {
        RagOrderCatalogQuery query = request == null
                ? RagOrderCatalogQuery.all()
                : new RagOrderCatalogQuery(
                        request.getRagId(),
                        request.getRagName(),
                        request.getKnowledgeTag(),
                        request.getStatus(),
                        request.getPageNum(),
                        request.getPageSize());
        return toResponses(ragOrderCatalog.queryList(query));
    }

    private RagOrderDefinition toDefinition(AiClientRagOrderRequestDTO request) {
        return new RagOrderDefinition(
                request == null ? null : request.getId(),
                request == null ? null : request.getRagId(),
                request == null ? null : request.getRagName(),
                request == null ? null : request.getKnowledgeTag(),
                request == null ? null : request.getStatus(),
                null,
                null);
    }

    private List<AiClientRagOrderResponseDTO> toResponses(List<RagOrderDefinition> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        return orders.stream().map(this::toResponse).toList();
    }

    private AiClientRagOrderResponseDTO toResponse(RagOrderDefinition order) {
        if (order == null) {
            return null;
        }
        return AiClientRagOrderResponseDTO.builder()
                .id(order.id())
                .ragId(order.ragId())
                .ragName(order.ragName())
                .knowledgeTag(order.knowledgeTag())
                .status(order.status())
                .createTime(order.createTime())
                .updateTime(order.updateTime())
                .build();
    }
}
