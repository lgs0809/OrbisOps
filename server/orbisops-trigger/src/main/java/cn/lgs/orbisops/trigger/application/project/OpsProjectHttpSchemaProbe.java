package cn.lgs.orbisops.trigger.application.project;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only HTTP discovery for RabbitMQ, Elasticsearch and Prometheus. */
@Slf4j
@Component
public class OpsProjectHttpSchemaProbe implements OpsProjectResourceSchemaProbe {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public boolean supports(String resourceType) {
        return "rabbitmq".equals(resourceType)
                || "elasticsearch".equals(resourceType)
                || "prometheus".equals(resourceType);
    }

    @Override
    public Map<String, Object> scan(String resourceType,
                                    String endpoint,
                                    Map<String, Object> credential)
            throws IOException, InterruptedException {
        return switch (resourceType) {
            case "rabbitmq" -> scanRabbitMq(endpoint, credential);
            case "elasticsearch" -> scanElasticsearch(endpoint, credential);
            case "prometheus" -> scanPrometheus(endpoint, credential);
            default -> Map.of("objects", List.of());
        };
    }

    private Map<String, Object> scanRabbitMq(String endpoint,
                                             Map<String, Object> credential)
            throws IOException, InterruptedException {
        JSONArray queues = JSON.parseArray(httpText(
                endpoint, "/api/queues/%2F", credential));
        List<Map<String, Object>> objects = new ArrayList<>();
        if (queues != null) {
            for (Object item : queues) {
                if (objects.size() >= 100 || !(item instanceof JSONObject queue)) {
                    break;
                }
                String name = text(queue.get("name"), "");
                if (StringUtils.hasText(name)) {
                    objects.add(Map.of(
                            "name", name,
                            "type", "QUEUE",
                            "description", "RabbitMQ queue",
                            "fields", List.of(
                                    "messages",
                                    "messages_ready",
                                    "messages_unacknowledged",
                                    "consumers")));
                }
            }
        }
        return schema(objects);
    }

    private Map<String, Object> scanElasticsearch(String endpoint,
                                                  Map<String, Object> credential)
            throws IOException, InterruptedException {
        JSONArray indices = JSON.parseArray(httpText(
                endpoint, "/_cat/indices?format=json&h=index", credential));
        List<Map<String, Object>> objects = new ArrayList<>();
        if (indices != null) {
            for (Object item : indices) {
                if (objects.size() >= 50) {
                    break;
                }
                if (!(item instanceof JSONObject indexRow)) {
                    continue;
                }
                String index = text(indexRow.get("index"), "");
                if (StringUtils.hasText(index)) {
                    objects.add(Map.of(
                            "name", index,
                            "fields", elasticsearchFields(endpoint, credential, index)));
                }
            }
        }
        return schema(objects);
    }

    private List<String> elasticsearchFields(String endpoint,
                                             Map<String, Object> credential,
                                             String index) {
        try {
            JSONObject root = JSON.parseObject(httpText(
                    endpoint,
                    "/" + urlEncode(index) + "/_mapping",
                    credential));
            if (root == null || root.isEmpty()) {
                return List.of();
            }
            JSONObject indexMapping = root.getJSONObject(index);
            if (indexMapping == null) {
                Object first = root.values().iterator().next();
                if (first instanceof JSONObject firstObject) {
                    indexMapping = firstObject;
                }
            }
            JSONObject mappings = indexMapping == null
                    ? null
                    : indexMapping.getJSONObject("mappings");
            JSONObject properties = mappings == null
                    ? null
                    : mappings.getJSONObject("properties");
            return properties == null || properties.isEmpty()
                    ? List.of()
                    : properties.keySet().stream().limit(16).toList();
        } catch (Exception e) {
            log.debug("读取 Elasticsearch mapping 失败，index={}", index, e);
            return List.of();
        }
    }

    private Map<String, Object> scanPrometheus(String endpoint,
                                               Map<String, Object> credential)
            throws IOException, InterruptedException {
        JSONObject response = JSON.parseObject(httpText(
                endpoint, "/api/v1/label/__name__/values", credential));
        JSONArray data = response == null ? null : response.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return schema(List.of());
        }
        Set<String> metrics = new LinkedHashSet<>();
        List<String> preferred = List.of(
                "up",
                "http_server_requests_seconds_count",
                "http_server_requests_seconds_sum",
                "jvm_memory_used_bytes",
                "jvm_memory_max_bytes",
                "process_cpu_usage");
        for (String metric : preferred) {
            if (data.contains(metric)) {
                metrics.add(metric);
            }
        }
        for (Object item : data) {
            if (metrics.size() >= 50) {
                break;
            }
            metrics.add(String.valueOf(item));
        }
        List<Map<String, Object>> objects = metrics.stream()
                .map(metric -> Map.<String, Object>of(
                        "name", metric,
                        "labels", List.of()))
                .toList();
        return schema(objects);
    }

    private Map<String, Object> schema(List<Map<String, Object>> objects) {
        return Map.of(
                "objects", objects,
                "scannedAt", LocalDateTime.now().toString());
    }

    private String httpText(String endpoint,
                            String pathAndQuery,
                            Map<String, Object> credential)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(joinUrl(endpoint, pathAndQuery)))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .header("Accept", "application/json");
        String username = text(credential.get("username"), "");
        String password = text(credential.get("password"), "");
        if (StringUtils.hasText(username) || StringUtils.hasText(password)) {
            String token = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String joinUrl(String endpoint, String pathAndQuery) {
        String base = text(endpoint, "http://127.0.0.1");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = pathAndQuery.startsWith("/")
                ? pathAndQuery
                : "/" + pathAndQuery;
        return base + path;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
