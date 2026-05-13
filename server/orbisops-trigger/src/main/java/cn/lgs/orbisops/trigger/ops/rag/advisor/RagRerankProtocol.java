package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Protocol ACL for Voyage/Cohere-compatible rerank services.
 */
@Slf4j
public final class RagRerankProtocol {

    private static final int CONNECT_TIMEOUT_SECONDS = 3;
    private static final int REQUEST_TIMEOUT_SECONDS = 45;
    private static final int ERROR_BODY_LIMIT = 180;

    private final RagRetrievalSettings settings;
    private final HttpTransport httpTransport;

    public RagRerankProtocol(RagRetrievalSettings settings) {
        this(settings, defaultTransport());
    }

    RagRerankProtocol(RagRetrievalSettings settings,
                      HttpTransport httpTransport) {
        this.settings = settings == null ? new RagRetrievalSettings() : settings;
        this.httpTransport = httpTransport;
    }

    public List<Document> rerank(String query,
                                 List<Document> candidates,
                                 RagRetrievalPlan plan,
                                 Map<String, Object> context) {
        if (!plan.rerankEnabled()
                || "none".equals(plan.rerankProvider())
                || candidates.size() <= 1) {
            return candidates;
        }
        if (!StringUtils.hasText(plan.rerankBaseUrl())
                || !StringUtils.hasText(settings.getRerankApiKey())) {
            rejectIfStrict(context, "RAG rerank 配置不完整，无法调用专用 reranker", null);
            return candidates;
        }

        try {
            return switch (plan.rerankProvider()) {
                case "voyage" -> voyage(query, candidates, plan);
                case "cohere" -> cohere(query, candidates, plan);
                default -> candidates;
            };
        } catch (Exception exception) {
            rejectIfStrict(context, "RAG rerank 失败：" + exception.getMessage(), exception);
            log.warn("RAG rerank 失败，降级使用融合召回排序：{}", exception.getMessage());
            return candidates;
        }
    }

    private List<Document> voyage(String query,
                                  List<Document> candidates,
                                  RagRetrievalPlan plan) throws Exception {
        JSONObject body = basePayload(query, candidates, plan);
        body.put("top_k", plan.rerankTopN());
        body.put("truncation", true);

        JSONObject response = postJson(endpoint(plan.rerankBaseUrl(), plan.rerankPath()), body.toJSONString());
        JSONArray results = Optional.ofNullable(response.getJSONArray("data"))
                .orElse(response.getJSONArray("results"));
        return applyResults(candidates, results, "voyage", plan.rerankTopN());
    }

    private List<Document> cohere(String query,
                                  List<Document> candidates,
                                  RagRetrievalPlan plan) throws Exception {
        JSONObject body = basePayload(query, candidates, plan);
        body.put("top_n", plan.rerankTopN());

        JSONObject response = postJson(endpoint(plan.rerankBaseUrl(), plan.rerankPath()), body.toJSONString());
        return applyResults(
                candidates,
                response.getJSONArray("results"),
                "cohere",
                plan.rerankTopN());
    }

    private JSONObject basePayload(String query,
                                   List<Document> candidates,
                                   RagRetrievalPlan plan) {
        JSONObject body = new JSONObject(true);
        body.put("model", plan.rerankModel());
        body.put("query", query);
        body.put("return_documents", false);
        JSONArray documents = new JSONArray();
        for (Document candidate : candidates) {
            documents.add(abbreviate(candidate.getText(), plan.rerankMaxDocChars()));
        }
        body.put("documents", documents);
        return body;
    }

    private List<Document> applyResults(List<Document> candidates,
                                        JSONArray results,
                                        String provider,
                                        int topN) {
        if (results == null || results.isEmpty()) {
            return candidates;
        }
        Map<Integer, Double> scores = parseScores(results, candidates.size());
        if (scores.isEmpty()) {
            return candidates;
        }

        List<ScoredDocument> scoredDocuments = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Document candidate = candidates.get(index);
            double score = scores.getOrDefault(index, -1d);
            Map<String, Object> metadata = new HashMap<>(candidate.getMetadata());
            metadata.put("reranked", true);
            metadata.put("rerank_provider", provider);
            if (score >= 0) {
                metadata.put("rerank_score", score);
            }
            scoredDocuments.add(new ScoredDocument(
                    new Document(candidate.getId(), candidate.getText(), metadata),
                    index,
                    score));
        }
        return scoredDocuments.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score)
                        .reversed()
                        .thenComparingInt(ScoredDocument::originalIndex))
                .limit(topN)
                .map(ScoredDocument::document)
                .collect(Collectors.toList());
    }

    private Map<Integer, Double> parseScores(JSONArray results, int candidateCount) {
        Map<Integer, Double> scores = new HashMap<>();
        for (int index = 0; index < results.size(); index++) {
            JSONObject item = results.getJSONObject(index);
            if (item == null) {
                continue;
            }
            Integer candidateIndex = item.getInteger("index");
            Double relevanceScore = item.getDouble("relevance_score");
            if (candidateIndex == null
                    || relevanceScore == null
                    || candidateIndex < 0
                    || candidateIndex >= candidateCount) {
                continue;
            }
            scores.put(candidateIndex, relevanceScore);
        }
        return scores;
    }

    private JSONObject postJson(String url, String body) throws Exception {
        HttpResult response = httpTransport.post(
                URI.create(url),
                body,
                settings.getRerankApiKey(),
                REQUEST_TIMEOUT_SECONDS);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Rerank HTTP " + response.statusCode() + " " + abbreviate(response.body(), ERROR_BODY_LIMIT));
        }
        return JSONObject.parseObject(response.body());
    }

    private void rejectIfStrict(Map<String, Object> context,
                                String message,
                                Exception cause) {
        if (context != null) {
            context.put("qa_rerank_error", message);
            context.put("qa_rerank_degraded", true);
        }
        if (booleanFromContext(context, "qa_rerank_fail_on_degradation", false)) {
            throw cause == null
                    ? new IllegalStateException(message)
                    : new IllegalStateException(message, cause);
        }
    }

    private boolean booleanFromContext(Map<String, Object> context,
                                       String key,
                                       boolean defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        Object value = context.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private String endpoint(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String normalizedPath = path.startsWith("/")
                ? path.substring(1)
                : path;
        return normalizedBaseUrl + "/" + normalizedPath;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static HttpTransport defaultTransport() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        return (uri, body, apiKey, timeoutSeconds) -> {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (StringUtils.hasText(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        };
    }

    @FunctionalInterface
    interface HttpTransport {
        HttpResult post(URI uri,
                        String body,
                        String apiKey,
                        int timeoutSeconds) throws Exception;
    }

    record HttpResult(int statusCode, String body) {
    }

    private record ScoredDocument(Document document,
                                  int originalIndex,
                                  double score) {
    }
}
