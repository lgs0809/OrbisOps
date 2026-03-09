package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;

import java.util.List;

public interface IAiClientRagOrderConfigRepository {

    int insert(AiClientConfigRecord record);

    int updateById(AiClientConfigRecord record);

    int updateByRagId(AiClientConfigRecord record);

    int deleteById(Long id);

    int deleteByRagId(String ragId);

    AiClientConfigRecord queryById(Long id);

    AiClientConfigRecord queryByRagId(String ragId);

    List<AiClientConfigRecord> queryEnabledRagOrders();

    List<AiClientConfigRecord> queryByKnowledgeTag(String knowledgeTag);

    List<AiClientConfigRecord> queryAll();
}
