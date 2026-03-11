package cn.lgs.orbisops.domain.knowledge.rag.repository;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagKnowledgeDocumentRecord;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagLexicalChunkRecord;

import java.util.List;
import java.util.Map;

public interface IRagKnowledgeRepository {

    boolean available();

    List<Map<String, Object>> listKnowledgeStats();

    Map<String, Object> documentStats(String tag);

    default Map<String, Object> documentStats(String tag, String scope, String projectId) {
        return documentStats(tag);
    }

    boolean deleteChunk(String chunkId);

    Map<String, Object> deleteChunksByTag(String tag);

    default Map<String, Object> deleteChunksByTag(String tag, String scope, String projectId) {
        return deleteChunksByTag(tag);
    }

    List<RagKnowledgeDocumentRecord> listDocuments(String tag);

    default List<RagKnowledgeDocumentRecord> listDocuments(String tag, String scope, String projectId) {
        return listDocuments(tag);
    }

    default boolean deleteChunk(String chunkId, String tag, String scope, String projectId) {
        return deleteChunk(chunkId);
    }

    RagKnowledgeDocumentRecord documentContent(String chunkId);

    List<RagDocument> searchVectorCandidates(String filterExpression,
                                             String vectorLiteral,
                                             int vectorDimensions,
                                             int topK);

    List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression,
                                                        List<String> candidateTerms,
                                                        int candidateLimit);

    void persistParsedDocument(String name,
                               String tag,
                               String fileName,
                               String contentType,
                               long fileSize,
                               List<RagDocument> documents);
}
