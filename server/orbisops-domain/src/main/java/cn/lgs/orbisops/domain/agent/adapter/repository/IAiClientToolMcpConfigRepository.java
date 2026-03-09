package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;

import java.util.List;

public interface IAiClientToolMcpConfigRepository {

    int insert(AiClientConfigRecord record);

    int updateById(AiClientConfigRecord record);

    int updateByMcpId(AiClientConfigRecord record);

    int deleteById(Long id);

    int deleteByMcpId(String mcpId);

    AiClientConfigRecord queryById(Long id);

    AiClientConfigRecord queryByMcpId(String mcpId);

    List<AiClientConfigRecord> queryAll();

    List<AiClientConfigRecord> queryByStatus(Integer status);

    List<AiClientConfigRecord> queryByTransportType(String transportType);

    List<AiClientConfigRecord> queryEnabledMcps();
}
