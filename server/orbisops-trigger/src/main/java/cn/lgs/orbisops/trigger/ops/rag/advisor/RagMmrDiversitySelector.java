package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Greedy maximal marginal relevance selection over fused or reranked documents.
 */
public final class RagMmrDiversitySelector {

    private static final double DEFAULT_LAMBDA = 0.72d;
    private static final double MIN_LAMBDA = 0.1d;
    private static final double MAX_LAMBDA = 0.95d;
    private static final List<String> RELEVANCE_KEYS = List.of(
            "rerank_score",
            "retrieval_score",
            "bm25_score");

    private final RagLexicalTextPolicy lexicalTextPolicy;

    public RagMmrDiversitySelector() {
        this(new RagLexicalTextPolicy());
    }

    RagMmrDiversitySelector(RagLexicalTextPolicy lexicalTextPolicy) {
        this.lexicalTextPolicy = lexicalTextPolicy;
    }

    public List<Document> select(String query,
                                 List<Document> candidates,
                                 int topK,
                                 Map<String, Object> context) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        boolean enabled = booleanFromContext(safeContext, "qa_mmr_enabled", true);
        if (!enabled || candidates.size() <= 2 || topK <= 1) {
            return candidates;
        }

        double lambda = clamp(
                doubleFromContext(safeContext, "qa_mmr_lambda", DEFAULT_LAMBDA),
                MIN_LAMBDA,
                MAX_LAMBDA);
        List<Document> remaining = new ArrayList<>(candidates);
        List<Document> selected = new ArrayList<>();
        while (!remaining.isEmpty() && selected.size() < topK) {
            Selection best = bestCandidate(query, remaining, selected, lambda);
            if (best == null) {
                break;
            }
            remaining.remove(best.document());
            selected.add(projectSelected(best.document(), best.score()));
        }
        selected.addAll(remaining);
        return selected;
    }

    private Selection bestCandidate(String query,
                                    List<Document> remaining,
                                    List<Document> selected,
                                    double lambda) {
        Document bestDocument = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Document candidate : remaining) {
            double relevance = relevanceScore(query, candidate);
            double diversityPenalty = selected.stream()
                    .mapToDouble(selectedDocument -> lexicalSimilarity(
                            candidate.getText(),
                            selectedDocument.getText()))
                    .max()
                    .orElse(0d);
            double score = lambda * relevance - (1 - lambda) * diversityPenalty;
            if (score > bestScore) {
                bestScore = score;
                bestDocument = candidate;
            }
        }
        return bestDocument == null ? null : new Selection(bestDocument, bestScore);
    }

    private double relevanceScore(String query, Document document) {
        Map<String, Object> metadata = document.getMetadata();
        for (String key : RELEVANCE_KEYS) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException ignored) {
                    // Continue to lower-priority metadata or lexical fallback.
                }
            }
        }
        return lexicalSimilarity(query, document.getText());
    }

    private double lexicalSimilarity(String left, String right) {
        Set<String> leftTokens = new HashSet<>(lexicalTextPolicy.tokenize(left));
        Set<String> rightTokens = new HashSet<>(lexicalTextPolicy.tokenize(right));
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0d;
        }
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0d : (double) intersection.size() / union.size();
    }

    private Document projectSelected(Document document, double score) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put("mmr_selected", true);
        metadata.put("mmr_score", score);
        return new Document(document.getId(), document.getText(), metadata);
    }

    private boolean booleanFromContext(Map<String, Object> context,
                                       String key,
                                       boolean defaultValue) {
        Object value = context.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private double doubleFromContext(Map<String, Object> context,
                                     String key,
                                     double defaultValue) {
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Selection(Document document, double score) {
    }
}
