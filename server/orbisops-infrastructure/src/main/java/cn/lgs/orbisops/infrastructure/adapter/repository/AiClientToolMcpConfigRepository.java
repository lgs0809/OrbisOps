package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientToolMcpDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientToolMcp;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis adapter for the typed MCP client catalog. */
@Repository
public class AiClientToolMcpConfigRepository implements McpClientCatalogPort {

    private final ObjectProvider<IAiClientToolMcpDao> daoProvider;

    public AiClientToolMcpConfigRepository(ObjectProvider<IAiClientToolMcpDao> daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public boolean insert(McpClientDefinition definition) {
        IAiClientToolMcpDao dao = dao();
        return dao != null && dao.insert(toPo(definition)) > 0;
    }

    @Override
    public boolean updateById(McpClientDefinition definition) {
        IAiClientToolMcpDao dao = dao();
        return dao != null && dao.updateById(toPo(definition)) > 0;
    }

    @Override
    public boolean updateByMcpId(McpClientDefinition definition) {
        IAiClientToolMcpDao dao = dao();
        return dao != null && dao.updateByMcpId(toPo(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        IAiClientToolMcpDao dao = dao();
        return dao != null && dao.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByMcpId(String mcpId) {
        IAiClientToolMcpDao dao = dao();
        return dao != null && dao.deleteByMcpId(mcpId) > 0;
    }

    @Override
    public McpClientDefinition findById(Long id) {
        IAiClientToolMcpDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryById(id));
    }

    @Override
    public McpClientDefinition findByMcpId(String mcpId) {
        IAiClientToolMcpDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryByMcpId(mcpId));
    }

    @Override
    public List<McpClientDefinition> listAll() {
        IAiClientToolMcpDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryAll());
    }

    @Override
    public List<McpClientDefinition> listByStatus(Integer status) {
        IAiClientToolMcpDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryByStatus(status));
    }

    @Override
    public List<McpClientDefinition> listByTransportType(String transportType) {
        IAiClientToolMcpDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryByTransportType(transportType));
    }

    @Override
    public List<McpClientDefinition> listEnabled() {
        IAiClientToolMcpDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryEnabledMcps());
    }

    private IAiClientToolMcpDao dao() {
        return daoProvider == null ? null : daoProvider.getIfAvailable();
    }

    private AiClientToolMcp toPo(McpClientDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("MCP_CLIENT_DEFINITION_REQUIRED");
        }
        return AiClientToolMcp.builder()
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

    private McpClientDefinition toDefinition(AiClientToolMcp po) {
        if (po == null) {
            return null;
        }
        return new McpClientDefinition(
                po.getId(),
                po.getMcpId(),
                po.getMcpName(),
                po.getTransportType(),
                po.getTransportConfig(),
                po.getRequestTimeout(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    private List<McpClientDefinition> definitions(List<AiClientToolMcp> rows) {
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream().map(this::toDefinition).toList();
    }
}
