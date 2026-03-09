package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Elasticsearch query DSL, HTTP transport, and response parsing boundary. */
final class OpsEsLogQueryProtocolService {

    private final Transport transport;

    OpsEsLogQueryProtocolService() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.transport = (url, body, timeoutSeconds) ->
                postJson(httpClient, url, body, timeoutSeconds);
    }

    OpsEsLogQueryProtocolService(Transport transport) {
        this.transport = transport;
    }

    Result execute(Input input) throws Exception {
        JSONObject query = buildQuery(input);
        JSONObject response = transport.post(
                input.elasticsearchUrl() + "/" + input.elasticsearchIndex() + "/_search",
                query.toJSONString(),
                input.timeoutSeconds());
        return parseResult(response);
    }

    JSONObject buildQuery(Input input) {
        JSONObject query = new JSONObject(true);
        query.put("size", input.includeRecentLogs() ? input.sampleSize() : 0);

        JSONArray sort = new JSONArray();
        JSONObject sortItem = new JSONObject(true);
        JSONObject sortOrder = new JSONObject(true);
        sortOrder.put("order", "desc");
        sortItem.put("@timestamp", sortOrder);
        sort.add(sortItem);
        query.put("sort", sort);

        JSONObject range = new JSONObject(true);
        JSONObject timestamp = new JSONObject(true);
        timestamp.put("gte", "now-" + input.rangeMinutes() + "m");
        timestamp.put("lte", "now");
        range.put("@timestamp", timestamp);

        JSONObject bool = new JSONObject(true);
        JSONArray filter = new JSONArray();
        JSONObject rangeWrapper = new JSONObject(true);
        rangeWrapper.put("range", range);
        filter.add(rangeWrapper);
        appendLogLevelFilter(filter, input.logLevels());
        bool.put("filter", filter);

        JSONArray must = new JSONArray();
        appendPhraseMust(must, input.mustPhrases());
        if (!must.isEmpty()) {
            bool.put("must", must);
        }
        appendPhraseShould(bool, input.shouldPhrases(), must.isEmpty());

        JSONObject queryNode = new JSONObject(true);
        queryNode.put("bool", bool);
        query.put("query", queryNode);

        JSONObject aggs = new JSONObject(true);
        aggs.put("levels", termsAgg("level.keyword", 10));
        aggs.put("top_loggers", termsAgg("logger_name.keyword", 8));
        query.put("aggs", aggs);
        return query;
    }

    Result parseResult(JSONObject response) {
        JSONObject hits = response.getJSONObject("hits");
        return new Result(
                readTotalHits(hits),
                readLevelCounts(response),
                readBuckets(response, "top_loggers"),
                readRecentLogs(hits));
    }

    private JSONObject termsAgg(String field, int size) {
        JSONObject terms = new JSONObject(true);
        terms.put("field", field);
        terms.put("size", size);
        JSONObject wrapper = new JSONObject(true);
        wrapper.put("terms", terms);
        return wrapper;
    }

    private void appendLogLevelFilter(JSONArray filter, List<String> logLevels) {
        if (logLevels.isEmpty()) {
            return;
        }
        JSONObject terms = new JSONObject(true);
        terms.put("level.keyword", logLevels);
        JSONObject wrapper = new JSONObject(true);
        wrapper.put("terms", terms);
        filter.add(wrapper);
    }

    private void appendPhraseMust(JSONArray must, List<String> phrases) {
        for (String phrase : phrases) {
            must.add(matchPhraseAcrossLogFields(phrase));
        }
    }

    private void appendPhraseShould(
            JSONObject bool,
            List<String> phrases,
            boolean requireOneMatch) {
        if (phrases.isEmpty()) {
            return;
        }
        JSONArray should = new JSONArray();
        for (String phrase : phrases) {
            should.add(matchPhraseAcrossLogFields(phrase));
        }
        bool.put("should", should);
        if (requireOneMatch) {
            bool.put("minimum_should_match", 1);
        }
    }

    private JSONObject matchPhraseAcrossLogFields(String phrase) {
        JSONArray should = new JSONArray();
        addMatchPhrase(should, "message", phrase);
        addMatchPhrase(should, "message.keyword", phrase);
        addMatchPhrase(should, "traceId", phrase);
        addMatchPhrase(should, "traceId.keyword", phrase);
        addMatchPhrase(should, "trace-id", phrase);
        addMatchPhrase(should, "trace-id.keyword", phrase);
        addMatchPhrase(should, "trace_id", phrase);
        addMatchPhrase(should, "trace_id.keyword", phrase);
        addMatchPhrase(should, "orderId", phrase);
        addMatchPhrase(should, "order_id", phrase);
        addMatchPhrase(should, "order_id.keyword", phrase);
        addMatchPhrase(should, "uri", phrase);
        addMatchPhrase(should, "uri.keyword", phrase);
        addMatchPhrase(should, "logger_name", phrase);
        addMatchPhrase(should, "logger_name.keyword", phrase);
        JSONObject bool = new JSONObject(true);
        bool.put("should", should);
        bool.put("minimum_should_match", 1);
        JSONObject wrapper = new JSONObject(true);
        wrapper.put("bool", bool);
        return wrapper;
    }

    private void addMatchPhrase(JSONArray should, String field, String phrase) {
        if (!org.springframework.util.StringUtils.hasText(phrase)) {
            return;
        }
        JSONObject fieldQuery = new JSONObject(true);
        fieldQuery.put(field, phrase);
        JSONObject wrapper = new JSONObject(true);
        wrapper.put("match_phrase", fieldQuery);
        should.add(wrapper);
    }

    private static JSONObject postJson(
            HttpClient httpClient,
            String url,
            String body,
            long timeoutSeconds) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "HTTP " + response.statusCode() + " " + abbreviate(response.body(), 180));
        }
        return JSONObject.parseObject(response.body());
    }

    private long readTotalHits(JSONObject hits) {
        if (hits == null) {
            return 0L;
        }
        Object total = hits.get("total");
        if (total instanceof JSONObject totalObject) {
            return totalObject.getLongValue("value");
        }
        if (total instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private Map<String, Long> readLevelCounts(JSONObject response) {
        List<Bucket> buckets = readBuckets(response, "levels");
        Map<String, Long> counts = new LinkedHashMap<>();
        buckets.forEach(bucket -> counts.put(bucket.key(), bucket.count()));
        counts.putIfAbsent("ERROR", 0L);
        counts.putIfAbsent("WARN", 0L);
        counts.putIfAbsent("INFO", 0L);
        return counts;
    }

    private List<Bucket> readBuckets(JSONObject response, String aggregationName) {
        JSONObject aggregations = response.getJSONObject("aggregations");
        if (aggregations == null || aggregations.getJSONObject(aggregationName) == null) {
            return new ArrayList<>();
        }
        JSONArray buckets = aggregations
                .getJSONObject(aggregationName)
                .getJSONArray("buckets");
        List<Bucket> result = new ArrayList<>();
        if (buckets == null) {
            return result;
        }
        for (int i = 0; i < buckets.size(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            result.add(new Bucket(
                    bucket.getString("key"),
                    bucket.getLongValue("doc_count")));
        }
        return result;
    }

    private List<LogSample> readRecentLogs(JSONObject hits) {
        List<LogSample> recentLogs = new ArrayList<>();
        if (hits == null || hits.getJSONArray("hits") == null) {
            return recentLogs;
        }
        JSONArray hitArray = hits.getJSONArray("hits");
        for (int i = 0; i < hitArray.size(); i++) {
            JSONObject source = hitArray.getJSONObject(i).getJSONObject("_source");
            if (source == null) {
                continue;
            }
            recentLogs.add(new LogSample(
                    source.getString("@timestamp"),
                    source.getString("level"),
                    source.getString("logger_name"),
                    source.getString("message")));
        }
        return recentLogs;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    record Input(
            String elasticsearchUrl,
            String elasticsearchIndex,
            long timeoutSeconds,
            int sampleSize,
            Integer rangeMinutes,
            boolean includeRecentLogs,
            List<String> logLevels,
            List<String> mustPhrases,
            List<String> shouldPhrases) {
    }

    record Result(
            long totalLogs,
            Map<String, Long> levelCounts,
            List<Bucket> topLoggers,
            List<LogSample> recentLogs) {
    }

    record Bucket(String key, long count) {
    }

    record LogSample(
            String timestamp,
            String level,
            String loggerName,
            String message) {
    }

    @FunctionalInterface
    interface Transport {
        JSONObject post(String url, String body, long timeoutSeconds) throws Exception;
    }
}
