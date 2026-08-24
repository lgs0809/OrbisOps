package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.fastjson.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.retry.support.RetryTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Exercises the actual HTTP/SSE -> Spring model -> Alibaba stream -> native MCP callback chain. */
class OpsMcpStreamingContractTest {
    @Test void nativeSerialToolPolicyPreservesFragmentedArgumentsAcrossAllFourChecks() throws Exception {
        var fixture = new OpsMcpDiscoveryFrameworkTest.Fixture(21, "a");
        var requests = new AtomicInteger();
        var failures = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        var modelArguments = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                var input = JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                assertThat(input.getBooleanValue("stream")).isTrue();
                assertThat(input.getBoolean("parallel_tool_calls")).isFalse();
                int round = requests.incrementAndGet();
                var chunks = new ArrayList<String>();
                if (round == 1) {
                    chunks.add(toolChunk(List.of(call(0, "search", "toolSearchTool", "{\"query\":\"metric_0 metric_1 metric_2 metric_3\"}"))));
                    chunks.add(finish("tool_calls"));
                } else if (round >= 2 && round <= 5) {
                    var offered = input.getJSONArray("tools");
                    for (int index = 0; index < 4; index++) {
                        String alias = fixture.alias("metric_" + index);
                        var schema = offered.stream().map(value -> JSON.parseObject(JSON.toJSONString(value)))
                                .map(value -> value.getJSONObject("function")).filter(value -> alias.equals(value.getString("name")))
                                .findFirst().orElseThrow().getJSONObject("parameters");
                        assertThat(schema.getString("type")).isEqualTo("object");
                        assertThat(schema.getJSONArray("required")).contains("service");
                    }
                    int index = round - 2;
                    chunks.add(toolChunk(List.of(call(0, "call-" + index, fixture.alias("metric_" + index), ""))));
                    for (String argument : List.of("{\"service\":", "\"orders\"}")) {
                        chunks.add(toolChunk(List.of(call(0, null, null, argument))));
                    }
                    chunks.add(finish("tool_calls"));
                } else {
                    assertThat(round).isEqualTo(6);
                    chunks.add(chunk(Map.of("content", "observed healthy"), null));
                    chunks.add(finish("stop"));
                }
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                try (var body = exchange.getResponseBody()) {
                    for (String value : chunks) { body.write(("data: " + value + "\n\n").getBytes(StandardCharsets.UTF_8)); body.flush(); }
                    body.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable failure) {
                failures.add(failure);
                exchange.close();
            }
        });
        server.start();
        try {
            var api = new OpsResilientOpenAiApi("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-key", "/v1/chat/completions", "/v1/embeddings", 2, 5, 10000, null);
            var model = OpenAiChatModel.builder().openAiApi(api)
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                    .defaultOptions(OpsAgentScopeConfigPolicy.toolCallingOptions().model("gpt-6-luna").build()).build();
            var audited = new org.springframework.ai.chat.model.ChatModel() {
                public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() { return model.getDefaultOptions(); }
                public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) { return model.call(prompt); }
                public reactor.core.publisher.Flux<org.springframework.ai.chat.model.ChatResponse> stream(org.springframework.ai.chat.prompt.Prompt prompt) {
                    return model.stream(prompt).doOnNext(value -> modelArguments.add(value.getResults().toString()));
                }
            };
            var agent = ReactAgent.builder().name("streaming_discovery").model(audited).tools(fixture.bundle.getTools())
                    .interceptors(fixture.interceptor, new OpsToolRoundSynthesisInterceptor(4)).build();
            agent.stream("Inspect all four orders metrics").collectList().block(Duration.ofSeconds(25));
            assertThat(failures).isEmpty();
            assertThat(fixture.businessCalls).withFailMessage("MODEL=%s", modelArguments).hasValue(4);
            assertThat(requests).hasValue(6);
        } finally { server.stop(0); }
    }

    private static Map<String, Object> call(int index, String id, String name, String arguments) {
        var function = new LinkedHashMap<String, Object>();
        if (name != null) function.put("name", name);
        function.put("arguments", arguments);
        var value = new LinkedHashMap<String, Object>(); value.put("index", index); value.put("function", function);
        if (id != null) { value.put("id", id); value.put("type", "function"); }
        return value;
    }
    private static String toolChunk(List<Map<String, Object>> calls) { return chunk(Map.of("tool_calls", calls), null); }
    private static String finish(String reason) { return chunk(Map.of(), reason); }
    private static String chunk(Map<String, Object> delta, String reason) {
        var choice = new LinkedHashMap<String, Object>(); choice.put("index", 0); choice.put("delta", delta);
        if (reason != null) choice.put("finish_reason", reason);
        return JSON.toJSONString(Map.of("id", "stream-contract", "object", "chat.completion.chunk", "created", 1,
                "model", "gpt-6-luna", "choices", List.of(choice)));
    }
}
