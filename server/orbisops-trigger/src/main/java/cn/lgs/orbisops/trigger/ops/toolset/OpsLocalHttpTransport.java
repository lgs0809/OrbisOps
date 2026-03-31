package cn.lgs.orbisops.trigger.ops.toolset;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Bounded and secret-masking HTTP transport for local observability adapters. */
public final class OpsLocalHttpTransport {

    private final OpsLocalAdapterSettings settings;
    private final Sender sender;

    public OpsLocalHttpTransport(OpsLocalAdapterSettings settings) {
        this(settings, defaultSender());
    }

    OpsLocalHttpTransport(
            OpsLocalAdapterSettings settings,
            Sender sender) {
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        if (sender == null) throw new IllegalArgumentException("LOCAL_HTTP_SENDER_REQUIRED");
        this.settings = settings;
        this.sender = sender;
    }

    public String get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .GET()
                .build();
        return send("GET", request);
    }

    public String postJson(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body == null ? "" : body,
                        StandardCharsets.UTF_8))
                .build();
        return send("POST", request);
    }

    private String send(String method, HttpRequest request) {
        try {
            return mask(abbreviate(sender.send(request), settings.maxResponseBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HTTP " + method + " 调用失败：" + e.getMessage(),
                    e);
        }
    }

    private String abbreviate(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String mask(String value) {
        return value
                .replaceAll("(?i)(password|passwd|pwd|secret|token|access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|authorization|bearer|jwt|session|cookie)\\s*[:=]\\s*[^\\s,;&\"}]+", "$1=***")
                .replaceAll("(?i)([a-z][a-z0-9+.-]*://[^\\s/@:]+:)([^\\s/@]+)(@)", "$1***$3");
    }

    private static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        return request -> client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    @FunctionalInterface
    interface Sender {
        String send(HttpRequest request) throws Exception;
    }
}
