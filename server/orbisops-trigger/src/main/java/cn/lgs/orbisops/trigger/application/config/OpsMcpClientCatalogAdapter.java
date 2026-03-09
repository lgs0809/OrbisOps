package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientToolMcpConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;

import java.util.List;

/** Repository adapter for the MCP client catalog. */
public final class OpsMcpClientCatalogAdapter implements McpClientCatalogPort {

    private final IAiClientToolMcpConfigRepository repository;

    public OpsMcpClientCatalogAdapter(IAiClientToolMcpConfigRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("MCP_CLIENT_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public boolean insert(McpClientDefinition definition) {
        return repository.insert(record(definition)) > 0;
    }

    @Override
    public boolean updateById(McpClientDefinition definition) {
        return repository.updateById(record(definition)) > 0;
    }

    @Override
    public boolean updateByMcpId(McpClientDefinition definition) {
        return repository.updateByMcpId(record(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByMcpId(String mcpId) {
        return repository.deleteByMcpId(mcpId) > 0;
    }

    @Override
    public McpClientDefinition findById(Long id) {
        return definition(repository.queryById(id));
    }

    @Override
    public McpClientDefinition findByMcpId(String mcpId) {
        return definition(repository.queryByMcpId(mcpId));
    }

    @Override
    public List<McpClientDefinition> listAll() {
        return definitions(repository.queryAll());
    }

    @Override
    public List<McpClientDefinition> listByStatus(Integer status) {
        return definitions(repository.queryByStatus(status));
    }

    @Override
    public List<McpClientDefinition> listByTransportType(String transportType) {
        return definitions(repository.queryByTransportType(transportType));
    }

    @Override
    public List<McpClientDefinition> listEnabled() {
        return definitions(repository.queryEnabledMcps());
    }

    private AiClientConfigRecord record(McpClientDefinition definition) {
        if (definition == null) {
            return null;
        }
        return AiClientConfigRecord.builder()
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

    private McpClientDefinition definition(AiClientConfigRecord record) {
        if (record == null) {
            return null;
        }
        return new McpClientDefinition(
                record.getId(),
                record.getMcpId(),
                record.getMcpName(),
                record.getTransportType(),
                record.getTransportConfig(),
                record.getRequestTimeout(),
                record.getStatus(),
                record.getCreateTime(),
                record.getUpdateTime());
    }

    private List<McpClientDefinition> definitions(List<AiClientConfigRecord> values) {
        return values == null || values.isEmpty()
                ? List.of()
                : values.stream().map(this::definition).toList();
    }
}
