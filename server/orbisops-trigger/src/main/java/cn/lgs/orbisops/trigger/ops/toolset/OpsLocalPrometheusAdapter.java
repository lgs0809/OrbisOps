package cn.lgs.orbisops.trigger.ops.toolset;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/** Prometheus local tool protocol adapter. */
public final class OpsLocalPrometheusAdapter {

    private final OpsLocalAdapterSettings settings;
    private final OpsLocalHttpTransport http;
    private final LongSupplier epochSeconds;

    public OpsLocalPrometheusAdapter(
            OpsLocalAdapterSettings settings,
            OpsLocalHttpTransport http) {
        this(settings, http, () -> Instant.now().getEpochSecond());
    }

    OpsLocalPrometheusAdapter(
            OpsLocalAdapterSettings settings,
            OpsLocalHttpTransport http,
            LongSupplier epochSeconds) {
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        if (http == null) throw new IllegalArgumentException("LOCAL_HTTP_TRANSPORT_REQUIRED");
        if (epochSeconds == null) throw new IllegalArgumentException("LOCAL_CLOCK_REQUIRED");
        this.settings = settings;
        this.http = http;
        this.epochSeconds = epochSeconds;
    }

    public Map<String, Object> execute(
            String toolName,
            OpsLocalToolArguments args) {
        String query = args.required("query", "Prometheus 查询必须提供 query");
        int rangeMinutes = args.boundedInt("rangeMinutes", 1, 240, 30);
        long end = epochSeconds.getAsLong();
        long start = end - rangeMinutes * 60L;
        String step = step(args.text("step", "30s"));
        String path = switch (toolName) {
            case "prometheus_instant_query", "prometheus_query" ->
                    "/api/v1/query?query=" + encode(query);
            case "prometheus_range_query" ->
                    "/api/v1/query_range?query=" + encode(query)
                            + "&start=" + encode(String.valueOf(start))
                            + "&end=" + encode(String.valueOf(end))
                            + "&step=" + encode(step);
            case "prometheus_label_values" ->
                    "/api/v1/label/" + encode(args.text("label", "__name__"))
                            + "/values";
            case "prometheus_series_query" ->
                    "/api/v1/series?match[]=" + encode(query)
                            + "&start=" + encode(String.valueOf(start))
                            + "&end=" + encode(String.valueOf(end));
            default -> throw new IllegalArgumentException(
                    "未知 Prometheus 工具：" + toolName);
        };
        return Map.of(
                "status", "SUCCEEDED",
                "adapter", "prometheus",
                "toolName", toolName,
                "response", http.get(settings.prometheusUrl() + path));
    }

    String step(String value) {
        String raw = (value == null ? "30s" : value).trim().toLowerCase(Locale.ROOT);
        if (!raw.matches("\\d+[smh]")) {
            throw new IllegalArgumentException(
                    "PROMETHEUS_STEP_INVALID：step 只能使用 1s/30s/1m/1h 这类受控格式");
        }
        int amount = Integer.parseInt(raw.substring(0, raw.length() - 1));
        char unit = raw.charAt(raw.length() - 1);
        long seconds = switch (unit) {
            case 's' -> amount;
            case 'm' -> amount * 60L;
            case 'h' -> amount * 3600L;
            default -> 30L;
        };
        if (seconds < 1 || seconds > 3600) {
            throw new IllegalArgumentException(
                    "PROMETHEUS_STEP_INVALID：step 必须在 1 秒到 1 小时之间");
        }
        return raw;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
