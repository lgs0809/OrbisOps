package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;

import java.util.List;

public interface IAiClientApiConfigRepository {

    int insert(AiClientConfigRecord record);

    int updateById(AiClientConfigRecord record);

    int updateByApiId(AiClientConfigRecord record);

    int deleteById(Long id);

    int deleteByApiId(String apiId);

    AiClientConfigRecord queryById(Long id);

    AiClientConfigRecord queryByApiId(String apiId);

    List<AiClientConfigRecord> queryEnabledApis();

    List<AiClientConfigRecord> queryAll();
}
