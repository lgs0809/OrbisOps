import cn.lgs.orbisops.trigger.ops.runtime.OpsResilientOpenAiApi;
import com.alibaba.fastjson.JSON;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.retry.support.RetryTemplate;

/** Actual Spring AI serialization to a local HTTP capture peer; no real model-quality claim. */
public final class ModelRequestContractProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Retain prior evidence; use a fresh output");
        var captures = new CopyOnWriteArrayList<Map<String,Object>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            var request = JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            captures.add(Map.of("keys", request.keySet(), "model", request.getString("model"),
                    "stream", request.getBooleanValue("stream"), "messageCount", request.getJSONArray("messages").size()));
            byte[] response = JSON.toJSONBytes(Map.of("id", "local-wire-capture", "object", "chat.completion",
                    "created", 1, "model", "gpt-5.6-luna", "choices", List.of(Map.of("index", 0,
                    "message", Map.of("role", "assistant", "content", "local protocol receipt"), "finish_reason", "stop"))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var api = new OpsResilientOpenAiApi("http://127.0.0.1:" + server.getAddress().getPort(),
                    "local-protocol-only", "/v1/chat/completions", "/v1/embeddings", 3, 60, 240000, null);
            var chat = OpenAiChatModel.builder().openAiApi(api)
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                    .defaultOptions(OpenAiChatOptions.builder().model("gpt-5.6-luna").build()).build();
            chat.call(new Prompt(new UserMessage("Local HTTP serialization diagnostic; no external inference.")));
            if (captures.size() != 1) throw new IllegalStateException("Exactly one physical HTTP request required");
            var keys = (java.util.Set<?>) captures.get(0).get("keys");
            var checks = Map.of("fixedModel", captures.get(0).get("model").equals("gpt-5.6-luna"),
                    "noTemperature", !keys.contains("temperature"), "noMaxTokens", !keys.contains("max_tokens"),
                    "noMaxCompletionTokens", !keys.contains("max_completion_tokens"),
                    "noReasoningEffort", !keys.contains("reasoning_effort"), "noTools", !keys.contains("tools"));
            Files.writeString(output, JSON.toJSONString(Map.of("recordedAt", java.time.Instant.now().toString(),
                    "scope", "ACTUAL_SDK_HTTP_SERIALIZATION_ONLY_NOT_REAL_PROVIDER_PROMPT_OR_MODEL_QUALITY",
                    "requests", captures, "checks", checks)) + "\n");
            if (checks.values().stream().anyMatch(value -> !value)) throw new IllegalStateException("Request contract differs");
        } finally { server.stop(0); }
    }
}
