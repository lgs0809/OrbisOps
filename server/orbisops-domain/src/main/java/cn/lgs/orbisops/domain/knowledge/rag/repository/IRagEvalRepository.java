package cn.lgs.orbisops.domain.knowledge.rag.repository;

import java.util.List;
import java.util.Map;

public interface IRagEvalRepository {

    void ensureTables();

    List<Map<String, Object>> listCases(Boolean enabled, int limit);

    Long saveCase(Long id,
                  String caseName,
                  String query,
                  String knowledgeTag,
                  String expectedKeywordsJson,
                  int topK,
                  boolean enabled);

    boolean deleteCase(Long id);

    List<Map<String, Object>> listEnabledCases(int limit);

    void saveRun(Map<String, Object> summaryJson);
}
