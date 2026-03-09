package cn.lgs.orbisops.application.knowledge;

import java.util.List;

public interface KnowledgeRagDocumentPort {

    List<KnowledgeRagChunk> list(String kbId, String scope, String projectId);

    KnowledgeDocumentStatistics statistics(String kbId, String scope, String projectId);

    boolean deleteChunk(String chunkId, String kbId, String scope, String projectId);

    boolean deleteChunk(String chunkId);

    KnowledgeChunkDeletionOutcome deleteChunks(String kbId, String scope, String projectId);

    KnowledgeRagChunk content(String chunkId);
}
