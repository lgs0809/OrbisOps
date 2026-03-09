package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.springframework.ai.document.Document;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministic reciprocal-rank fusion over typed recall results.
 */
public final class RagReciprocalRankFusion {

    public List<Document> fuse(List<RagRankedDocument> rankedDocuments,
                               RagRetrievalPlan plan) {
        return rankedDocuments.stream()
                .collect(Collectors.groupingBy(
                        RagRankedDocument::key,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values()
                .stream()
                .map(this::merge)
                .sorted(Comparator.comparingDouble(RagRankedDocument::score).reversed())
                .limit(candidateLimit(plan))
                .map(RagRankedDocument::document)
                .collect(Collectors.toList());
    }

    private RagRankedDocument merge(List<RagRankedDocument> documents) {
        RagRankedDocument first = documents.get(0);
        double score = documents.stream()
                .mapToDouble(RagRankedDocument::score)
                .sum();
        Map<String, Object> metadata = new HashMap<>(first.document().getMetadata());
        metadata.put("retrieval_sources", documents.stream()
                .map(RagRankedDocument::source)
                .distinct()
                .collect(Collectors.joining(",")));
        metadata.put("retrieval_score", score);
        Document document = new Document(
                first.document().getId(),
                first.document().getText(),
                metadata);
        return new RagRankedDocument(document, "hybrid", first.rank(), score);
    }

    private int candidateLimit(RagRetrievalPlan plan) {
        return plan.rerankEnabled()
                ? plan.rerankCandidateTopK()
                : plan.finalTopK();
    }
}
