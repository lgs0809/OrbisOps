package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Ordered RRF fusion and final semantic ranking policy. */
public class SemanticMemoryFusionPolicy {

    private final SemanticMemoryPolicy semanticPolicy;

    public SemanticMemoryFusionPolicy(SemanticMemoryPolicy semanticPolicy) {
        this.semanticPolicy = semanticPolicy == null
                ? new SemanticMemoryPolicy(new MemoryContentHashPolicy())
                : semanticPolicy;
    }

    public List<SemanticMemoryDocumentSnapshot> fuseAndRank(
            List<SemanticMemoryRankedCandidate> candidates,
            int limit,
            boolean recencyAware,
            double recencyHalfLifeTurns) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<SemanticMemoryDocumentSnapshot> fused = candidates.stream()
                .filter(candidate -> candidate != null && candidate.document() != null)
                .collect(Collectors.groupingBy(
                        candidate -> semanticPolicy.documentKey(
                                candidate.document().id(),
                                candidate.document().content(),
                                candidate.document().metadata()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values()
                .stream()
                .map(this::merge)
                .toList();
        long currentTurn = fused.stream()
                .mapToLong(document -> semanticPolicy.turnIndex(document.metadata()))
                .max()
                .orElse(0L);
        return fused.stream()
                .sorted(Comparator.comparingDouble((SemanticMemoryDocumentSnapshot document) ->
                        semanticPolicy.score(
                                document.metadata(),
                                currentTurn,
                                recencyAware,
                                recencyHalfLifeTurns)).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    SemanticMemoryDocumentSnapshot merge(List<SemanticMemoryRankedCandidate> candidates) {
        SemanticMemoryRankedCandidate first = candidates.get(0);
        Map<String, Object> metadata = new HashMap<>(first.document().metadata());
        double rrfScore = candidates.stream()
                .mapToDouble(SemanticMemoryRankedCandidate::rrfScore)
                .sum();
        metadata.put("memory_rrf_score", rrfScore);
        metadata.put("memory_retrieval_sources", candidates.stream()
                .map(SemanticMemoryRankedCandidate::source)
                .distinct()
                .collect(Collectors.joining(",")));
        candidates.stream()
                .filter(candidate -> "vector".equals(candidate.source()))
                .findFirst()
                .ifPresent(candidate -> metadata.put("memory_vector_rank", candidate.rank()));
        candidates.stream()
                .filter(candidate -> "lexical".equals(candidate.source()))
                .findFirst()
                .ifPresent(candidate -> metadata.put("memory_lexical_rank", candidate.rank()));
        return new SemanticMemoryDocumentSnapshot(
                first.document().id(),
                first.document().content(),
                metadata);
    }
}
