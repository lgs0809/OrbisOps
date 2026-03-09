package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientToolMcpQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientToolMcpRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientToolMcpResponseDTO;
import cn.lgs.orbisops.application.config.McpClientCatalogQuery;
import cn.lgs.orbisops.application.config.McpClientCatalogUseCase;
import cn.lgs.orbisops.application.config.McpClientCommand;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

/** HTTP-facing facade for MCP client catalog configuration. */
@Service
public class AiClientToolMcpApplicationService {

    private final McpClientCatalogUseCase catalogUseCase;

    public AiClientToolMcpApplicationService(McpClientCatalogUseCase catalogUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CATALOG_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
    }

    public boolean create(AiClientToolMcpRequestDTO request) {
        return catalogUseCase.create(toCommand(request));
    }

    public boolean updateById(AiClientToolMcpRequestDTO request) {
        return catalogUseCase.updateById(toCommand(request));
    }

    public boolean updateByMcpId(AiClientToolMcpRequestDTO request) {
        return catalogUseCase.updateByMcpId(toCommand(request));
    }

    public boolean deleteById(Long id) {
        return catalogUseCase.deleteById(id);
    }

    public boolean deleteByMcpId(String mcpId) {
        return catalogUseCase.deleteByMcpId(mcpId);
    }

    public AiClientToolMcpResponseDTO queryById(Long id) {
        return toResponse(catalogUseCase.findById(id));
    }

    public AiClientToolMcpResponseDTO queryByMcpId(String mcpId) {
        return toResponse(catalogUseCase.findByMcpId(mcpId));
    }

    public List<AiClientToolMcpResponseDTO> queryAll() {
        return toResponses(catalogUseCase.listAll());
    }

    public List<AiClientToolMcpResponseDTO> queryByStatus(Integer status) {
        return toResponses(catalogUseCase.listByStatus(status));
    }

    public List<AiClientToolMcpResponseDTO> queryByTransportType(String transportType) {
        return toResponses(catalogUseCase.listByTransportType(transportType));
    }

    public List<AiClientToolMcpResponseDTO> queryEnabled() {
        return toResponses(catalogUseCase.listEnabled());
    }

    public List<AiClientToolMcpResponseDTO> queryList(AiClientToolMcpQueryRequestDTO request) {
        return toResponses(catalogUseCase.query(toQuery(request)));
    }

    private McpClientCommand toCommand(AiClientToolMcpRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new McpClientCommand(
                request.getId(),
                request.getMcpId(),
                request.getMcpName(),
                request.getTransportType(),
                request.getTransportConfig(),
                request.getRequestTimeout(),
                request.getStatus());
    }

    private McpClientCatalogQuery toQuery(AiClientToolMcpQueryRequestDTO request) {
        if (request == null) {
            return McpClientCatalogQuery.all();
        }
        return new McpClientCatalogQuery(
                request.getMcpId(),
                request.getMcpName(),
                request.getTransportType(),
                request.getStatus());
    }

    private List<AiClientToolMcpResponseDTO> toResponses(List<McpClientDefinition> definitions) {
        return definitions == null || definitions.isEmpty()
                ? List.of()
                : definitions.stream().map(this::toResponse).toList();
    }

    private AiClientToolMcpResponseDTO toResponse(McpClientDefinition definition) {
        if (definition == null) {
            return null;
        }
        return AiClientToolMcpResponseDTO.builder()
                .id(definition.id())
                .mcpId(definition.mcpId())
                .mcpName(definition.mcpName())
                .transportType(definition.transportType())
                .transportConfig(definition.transportConfig())
                .requestTimeout(definition.requestTimeout())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
