package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;

import java.util.List;

public interface IAiClientModelConfigRepository {

    int insert(AiClientConfigRecord record);

    int updateById(AiClientConfigRecord record);

    int updateByModelId(AiClientConfigRecord record);

    int deleteById(Long id);

    int deleteByModelId(String modelId);

    AiClientConfigRecord queryById(Long id);

    AiClientConfigRecord queryByModelId(String modelId);

    List<AiClientConfigRecord> queryByApiId(String apiId);

    List<AiClientConfigRecord> queryByModelType(String modelType);

    List<AiClientConfigRecord> queryEnabledModels();

    List<AiClientConfigRecord> queryAll();
}
