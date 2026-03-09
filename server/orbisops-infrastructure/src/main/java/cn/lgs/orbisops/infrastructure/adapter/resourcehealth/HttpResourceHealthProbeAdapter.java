package cn.lgs.orbisops.infrastructure.adapter.resourcehealth;

import cn.lgs.orbisops.application.resourcehealth.ElasticsearchResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.PrometheusResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP adapter for Elasticsearch and Prometheus resource probes. */
@Component
public class HttpResourceHealthProbeAdapter implements
        ElasticsearchResourceHealthProbePort,
        PrometheusResourceHealthProbePort {

    private final HttpClient httpClient;

    public HttpResourceHealthProbeAdapter() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    HttpResourceHealthProbeAdapter(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ResourceHealthCheck probe(String baseUrl, String index) {
        String endpoint = text(baseUrl);
        if (endpoint.isBlank()) {
            return ResourceHealthCheck.unavailable(
                    "elasticsearch",
                    "Elasticsearch 日志",
                    endpoint,
                    "未配置地址");
        }
        try {
            HttpResponse<String> cluster = request(
                    endpoint,
                    "/_cluster/health",
                    "GET",
                    null);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("clusterStatusCode", cluster.statusCode());
            details.put("clusterStatus", jsonValue(cluster.body(), "status"));
            boolean clusterHealthy = successful(cluster.statusCode());
            String configuredIndex = text(index);
            if (!configuredIndex.isBlank()) {
                HttpResponse<String> indexProbe = request(
                        endpoint,
                        "/" + configuredIndex + "/_search",
                        "POST",
                        "{\"size\":0,\"track_total_hits\":false,\"query\":{\"match_all\":{}}}");
                details.put("index", configuredIndex);
                details.put("indexStatusCode", indexProbe.statusCode());
                boolean healthy = clusterHealthy
                        && successful(indexProbe.statusCode());
                return new ResourceHealthCheck(
                        "elasticsearch",
                        "Elasticsearch 日志",
                        endpoint,
                        healthy,
                        healthy
                                ? "集群和配置的日志索引最小查询正常"
                                : "集群或配置的日志索引查询异常",
                        details);
            }
            return new ResourceHealthCheck(
                    "elasticsearch",
                    "Elasticsearch 日志",
                    endpoint,
                    clusterHealthy,
                    clusterHealthy
                            ? "集群可访问；未配置全局日志索引，项目 Agent 应使用节点绑定的 Elasticsearch MCP"
                            : "Elasticsearch 集群查询异常",
                    details);
        } catch (Exception error) {
            return ResourceHealthCheck.unavailable(
                    "elasticsearch",
                    "Elasticsearch 日志",
                    endpoint,
                    errorMessage(error));
        }
    }

    @Override
    public ResourceHealthCheck probe(
            String baseUrl,
            String job,
            String instance) {
        String endpoint = text(baseUrl);
        String promql = prometheusUpQuery(job, instance);
        if (endpoint.isBlank()) {
            return new ResourceHealthCheck(
                    "prometheus",
                    "Prometheus 指标",
                    endpoint,
                    false,
                    "未配置地址",
                    Map.of("promql", promql));
        }
        try {
            HttpResponse<String> response = request(
                    endpoint,
                    "/api/v1/query?query=" + URLEncoder.encode(
                            promql,
                            StandardCharsets.UTF_8),
                    "GET",
                    null);
            JSONObject body = JSON.parseObject(response.body());
            int resultCount = body == null
                    || body.getJSONObject("data") == null
                    || body.getJSONObject("data").getJSONArray("result") == null
                    ? 0
                    : body.getJSONObject("data").getJSONArray("result").size();
            boolean healthy = successful(response.statusCode())
                    && body != null
                    && "success".equalsIgnoreCase(body.getString("status"))
                    && resultCount > 0;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("promql", promql);
            details.put("statusCode", response.statusCode());
            details.put("resultCount", resultCount);
            return new ResourceHealthCheck(
                    "prometheus",
                    "Prometheus 指标",
                    endpoint,
                    healthy,
                    healthy
                            ? "目标 up 指标可查询"
                            : "Prometheus 可访问但目标 up 指标未命中",
                    details);
        } catch (Exception error) {
            return new ResourceHealthCheck(
                    "prometheus",
                    "Prometheus 指标",
                    endpoint,
                    false,
                    errorMessage(error),
                    Map.of("promql", promql));
        }
    }

    private HttpResponse<String> request(
            String baseUrl,
            String path,
            String method,
            String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(stripTrailingSlash(baseUrl) + path))
                .timeout(Duration.ofSeconds(5));
        if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            body == null ? "{}" : body));
        } else {
            builder.GET();
        }
        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String prometheusUpQuery(String job, String instance) {
        List<String> labels = new ArrayList<>();
        if (!text(job).isBlank()) {
            labels.add("job=\"" + escapeLabel(job) + "\"");
        }
        if (!text(instance).isBlank()) {
            labels.add("instance=\"" + escapeLabel(instance) + "\"");
        }
        return labels.isEmpty()
                ? "up"
                : "up{" + String.join(",", labels) + "}";
    }

    private String jsonValue(String body, String key) {
        try {
            JSONObject object = JSON.parseObject(body);
            return object == null ? "" : text(object.getString(key));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private boolean successful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private String escapeLabel(String value) {
        return text(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String stripTrailingSlash(String value) {
        String result = text(value);
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String errorMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
