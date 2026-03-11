package cn.lgs.orbisops.domain.knowledge.rag.repository;

import java.util.List;
import java.util.Map;

public interface IRagFeedbackRepository {

    void ensureTables();

    Long insertFeedback(String query,
                        String answer,
                        Boolean useful,
                        Boolean resolved,
                        String sourceType,
                        String sourceId,
                        String knowledgeTag,
                        String chunkIdsJson,
                        String comment);

    Map<String, Object> upsertGap(String query, String knowledgeTag, String comment);

    List<Map<String, Object>> listFeedback(String knowledgeTag, Boolean useful, Boolean resolved, int limit);

    List<Map<String, Object>> listGaps(String status, String knowledgeTag, int limit);

    boolean updateGapStatus(Long id, String status);

    Map<String, Object> queryGap(Long id);
}
