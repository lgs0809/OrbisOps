package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
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
import java.util.stream.Collectors;

/** PromQL, HTTP transport, vector parsing, and metric calculation boundary. */
final class OpsPrometheusQueryProtocolService {

    private final Transport transport;

    OpsPrometheusQueryProtocolService() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.transport = (url, timeoutSeconds) ->
                getJson(httpClient, url, timeoutSeconds);
    }

    OpsPrometheusQueryProtocolService(Transport transport) {
        this.transport = transport;
    }

    Result execute(Input input, CancellationCheck cancellationCheck) throws Exception {
        List<VectorSample> upMetrics = vector(
                "up" + selector(input.jobName()),
                input,
                cancellationCheck);
        int instanceTotal = upMetrics.size();
        int instanceUp = (int) upMetrics.stream()
                .filter(metric -> Double.compare(metric.value(), 1D) == 0)
                .count();

        String httpSelector = httpMetricSelector(input.jobName(), input.primaryUri());
        List<VectorSample> qpsVector = vector(
                "sum(rate(http_server_requests_seconds_count{" + httpSelector + "}["
                        + input.promWindow() + "])) by (uri,method,status)",
                input,
                cancellationCheck);
        Map<String, Double> avgResponseMs = readAvgResponseMs(
                input,
                httpSelector,
                cancellationCheck);
        List<EndpointMetric> endpointMetrics = qpsVector.stream()
                .map(item -> new EndpointMetric(
                        item.labels().get("uri"),
                        item.labels().get("method"),
                        item.labels().get("status"),
                        round(item.value(), 4),
                        round(avgResponseMs.getOrDefault(item.labels().get("uri"), 0D), 2)))
                .sorted(Comparator.comparingDouble(EndpointMetric::qps).reversed())
                .limit(20)
                .collect(Collectors.toList());

        double totalQps = endpointMetrics.stream()
                .mapToDouble(EndpointMetric::qps)
                .sum();
        double errorQps = singleValue(
                "sum(rate(http_server_requests_seconds_count{" + httpSelector
                        + ",status=~\"5..\"}[" + input.promWindow() + "]))",
                input,
                cancellationCheck);
        double errorRate = totalQps <= 0D ? 0D : errorQps / totalQps * 100D;
        double heapUsage = singleValue(
                "avg(jvm_memory_used_bytes" + selector(input.jobName(), "area=\"heap\"")
                        + ") / avg(jvm_memory_max_bytes"
                        + selector(input.jobName(), "area=\"heap\"") + ") * 100",
                input,
                cancellationCheck);
        double processCpuUsage = singleValue(
                "avg(process_cpu_usage" + selector(input.jobName()) + ") * 100",
                input,
                cancellationCheck);

        return new Result(
                instanceTotal,
                instanceUp,
                round(totalQps, 4),
                round(errorQps, 4),
                round(errorRate, 2),
                round(heapUsage, 2),
                round(processCpuUsage, 2),
                endpointMetrics);
    }

    private Map<String, Double> readAvgResponseMs(
            Input input,
            String httpSelector,
            CancellationCheck cancellationCheck) throws Exception {
        String query = "sum(rate(http_server_requests_seconds_sum{" + httpSelector + "}["
                + input.promWindow() + "])) by (uri) / "
                + "sum(rate(http_server_requests_seconds_count{" + httpSelector + "}["
                + input.promWindow() + "])) by (uri)";
        Map<String, Double> values = new HashMap<>();
        for (VectorSample item : vector(query, input, cancellationCheck)) {
            values.put(item.labels().get("uri"), item.value() * 1000D);
        }
        return values;
    }

    private double singleValue(
            String query,
            Input input,
            CancellationCheck cancellationCheck) throws Exception {
        List<VectorSample> vector = vector(query, input, cancellationCheck);
        return vector.isEmpty() ? 0D : vector.get(0).value();
    }

    private List<VectorSample> vector(
            String query,
            Input input,
            CancellationCheck cancellationCheck) throws Exception {
        cancellationCheck.check();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        JSONObject response = transport.get(
                input.prometheusUrl() + "/api/v1/query?query=" + encodedQuery,
                input.timeoutSeconds());
        cancellationCheck.check();
        if (!"success".equals(response.getString("status"))) {
            throw new IllegalStateException(
                    "Prometheus 查询失败：" + response.getString("error"));
        }
        JSONArray result = response.getJSONObject("data").getJSONArray("result");
        List<VectorSample> samples = new ArrayList<>();
        if (result == null) {
            return samples;
        }
        for (int i = 0; i < result.size(); i++) {
            JSONObject item = result.getJSONObject(i);
            JSONObject metric = item.getJSONObject("metric");
            Map<String, String> labels = new HashMap<>();
            if (metric != null) {
                for (String key : metric.keySet()) {
                    labels.put(key, metric.getString(key));
                }
            }
            samples.add(new VectorSample(labels, readValue(item)));
        }
        return samples;
    }

    private String httpMetricSelector(String jobName, String primaryUri) {
        List<String> labels = new ArrayList<>();
        if (hasText(jobName)) {
            labels.add("job=\"" + escapeLabel(jobName) + "\"");
        }
        labels.add("uri!=\"/actuator/prometheus\"");
        if (hasText(primaryUri)) {
            labels.add("uri=~\".*" + escapeRegex(primaryUri) + ".*\"");
        }
        return String.join(",", labels);
    }

    private String selector(String jobName, String... additionalLabels) {
        List<String> labels = new ArrayList<>();
        if (hasText(jobName)) {
            labels.add("job=\"" + escapeLabel(jobName) + "\"");
        }
        if (additionalLabels != null) {
            for (String label : additionalLabels) {
                if (hasText(label)) {
                    labels.add(label);
                }
            }
        }
        return labels.isEmpty() ? "" : "{" + String.join(",", labels) + "}";
    }

    private String escapeLabel(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeRegex(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace(".", "\\.")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("+", "\\+")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("|", "\\|");
    }

    private double readValue(JSONObject item) {
        JSONArray value = item.getJSONArray("value");
        if (value == null || value.size() < 2) {
            return 0D;
        }
        try {
            double parsed = Double.parseDouble(value.getString(1));
            return Double.isNaN(parsed) || Double.isInfinite(parsed) ? 0D : parsed;
        } catch (Exception e) {
            return 0D;
        }
    }

    private double round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static JSONObject getJson(
            HttpClient httpClient,
            String url,
            long timeoutSeconds) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
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

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    record Input(
            String prometheusUrl,
            String jobName,
            long timeoutSeconds,
            String promWindow,
            String primaryUri) {
    }

    record Result(
            int instanceTotal,
            int instanceUp,
            double totalQps,
            double errorQps,
            double errorRate,
            double heapMemoryUsagePercent,
            double processCpuUsagePercent,
            List<EndpointMetric> endpointMetrics) {
    }

    record EndpointMetric(
            String uri,
            String method,
            String status,
            double qps,
            double avgResponseMs) {
    }

    private record VectorSample(Map<String, String> labels, double value) {
    }

    @FunctionalInterface
    interface Transport {
        JSONObject get(String url, long timeoutSeconds) throws Exception;
    }

    @FunctionalInterface
    interface CancellationCheck {
        void check();
    }
}
