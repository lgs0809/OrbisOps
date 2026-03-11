package cn.lgs.orbisops.domain.knowledge.rag.repository;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;

import java.util.List;
import java.util.Map;

public interface IRagMultimodalRepository {

    boolean available();

    boolean ensureTable(String tableName, int dimension);

    List<RagDocument> search(String tableName,
                             String vectorLiteral,
                             int dimension,
                             String filterExpression,
                             int topK,
                             String provider,
                             String model);

    void upsert(String tableName,
                int dimension,
                String id,
                String content,
                Map<String, Object> metadata,
                String vectorLiteral);
}
