package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryFusionPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;

import java.util.ArrayList;
import java.util.List;

/** Application orchestration for vector/lexical recall and semantic fusion. */
public class SemanticMemoryRetrievalApplicationService {

    private final SemanticVectorRecallPort vectorRecallPort;
    private final SemanticLexicalRecallPort lexicalRecallPort;
    private final SemanticMemoryRetrievalFailurePort failurePort;
    private final SemanticMemoryFusionPolicy fusionPolicy;
    private final cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository sources;

    public SemanticMemoryRetrievalApplicationService(
            SemanticVectorRecallPort vectorRecallPort,
            SemanticLexicalRecallPort lexicalRecallPort,
            SemanticMemoryRetrievalFailurePort failurePort,
            SemanticMemoryFusionPolicy fusionPolicy) {
        this(vectorRecallPort,lexicalRecallPort,failurePort,fusionPolicy,null);
    }

    public SemanticMemoryRetrievalApplicationService(SemanticVectorRecallPort vectorRecallPort,SemanticLexicalRecallPort lexicalRecallPort,
            SemanticMemoryRetrievalFailurePort failurePort,SemanticMemoryFusionPolicy fusionPolicy,
            cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository sources) {
        this.sources=sources;
        this.vectorRecallPort = vectorRecallPort;
        this.lexicalRecallPort = lexicalRecallPort;
        this.failurePort = failurePort;
        this.fusionPolicy = fusionPolicy == null
                ? new SemanticMemoryFusionPolicy(
                        new SemanticMemoryPolicy(new MemoryContentHashPolicy()))
                : fusionPolicy;
    }

    public static SemanticMemoryRetrievalApplicationService withDefaultFusion(
            SemanticVectorRecallPort vectorRecallPort,
            SemanticLexicalRecallPort lexicalRecallPort,
            SemanticMemoryRetrievalFailurePort failurePort) {
        return new SemanticMemoryRetrievalApplicationService(
                vectorRecallPort,
                lexicalRecallPort,
                failurePort,
                new SemanticMemoryFusionPolicy(
                        new SemanticMemoryPolicy(new MemoryContentHashPolicy())));
    }

    public List<SemanticMemoryDocumentSnapshot> search(SemanticMemoryRetrievalQuery query) {
        if (query == null || !hasText(query.sessionId()) || !hasText(query.query())) {
            return List.of();
        }
        int recallLimit = recallLimit(query.limit(), query.semanticTopK());
        List<SemanticMemoryRankedCandidate> candidates = new ArrayList<>();
        if (query.embeddingAvailable() && vectorRecallPort != null) {
            candidates.addAll(recallVector(query, recallLimit));
        }
        if (lexicalRecallPort != null) {
            candidates.addAll(recallLexical(query, recallLimit));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            if(sources!=null) {
                var references=candidates.stream().map(candidate->projection(candidate.document())).filter(java.util.Objects::nonNull).toList();
                String project=references.isEmpty()?"":sources.window(query.sessionId(),2,false)
                        .map(cn.lgs.orbisops.domain.memory.model.ConversationMemoryWindow::projectId).orElse("");
                var visible=references.isEmpty()?java.util.Set.<String>of():sources.visibleSemanticProjections(query.sessionId(),project,query.userId(),references);
                candidates.removeIf(candidate->projection(candidate.document())!=null && !visible.contains(candidate.document().id()));
            }
            return fusionPolicy.fuseAndRank(
                    candidates,
                    query.limit(),
                    query.recencyAware(),
                    query.recencyHalfLifeTurns());
        } catch (RuntimeException error) {
            observeFailure("fusion", error);
            return List.of();
        }
    }

    int recallLimit(int limit, int semanticTopK) {
        return Math.max(1, Math.min(Math.max(limit, semanticTopK) * 4, 40));
    }

    private cn.lgs.orbisops.domain.memory.model.SemanticProjectionReference projection(SemanticMemoryDocumentSnapshot document) {
        var metadata=document.metadata();Object id=metadata.get("semantic_job_id");
        if(!(id instanceof Number job))return null;
        long epoch=metadata.get("semantic_epoch") instanceof Number value?value.longValue():-1;
        return new cn.lgs.orbisops.domain.memory.model.SemanticProjectionReference(document.id(),job.longValue(),epoch,
                java.util.Objects.toString(metadata.get("semantic_source_hash"),""),java.util.Objects.toString(metadata.get("semantic_projection"),""));
    }

    private List<SemanticMemoryRankedCandidate> recallVector(
            SemanticMemoryRetrievalQuery query,
            int recallLimit) {
        try {
            List<SemanticMemoryRankedCandidate> result = vectorRecallPort.recallVector(
                    query.sessionId(),
                    query.userId(),
                    query.query(),
                    recallLimit);
            return result == null ? List.of() : result;
        } catch (RuntimeException error) {
            observeFailure("vector-recall", error);
            return List.of();
        }
    }

    private List<SemanticMemoryRankedCandidate> recallLexical(
            SemanticMemoryRetrievalQuery query,
            int recallLimit) {
        try {
            List<SemanticMemoryRankedCandidate> result = lexicalRecallPort.recallLexical(
                    query.sessionId(),
                    query.userId(),
                    query.query(),
                    recallLimit);
            return result == null ? List.of() : result;
        } catch (RuntimeException error) {
            observeFailure("lexical-recall", error);
            return List.of();
        }
    }

    private void observeFailure(String operation, RuntimeException error) {
        if (failurePort == null) {
            return;
        }
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
