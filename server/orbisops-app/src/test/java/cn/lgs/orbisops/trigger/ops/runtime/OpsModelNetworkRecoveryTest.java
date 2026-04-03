package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Real loopback HTTP/SSE faults. Provider payloads are fixtures, not real-model quality evidence. */
class OpsModelNetworkRecoveryTest {
    private static final String MODEL = "gpt-5.6-luna";
    private final List<OpsRuntimeEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final java.util.concurrent.ExecutorService httpWorkers = Executors.newCachedThreadPool();
    private HttpServer server;
    private OpsResilientOpenAiApi api;
    private final Map<HttpExchange, String> requestBodies = new java.util.concurrent.ConcurrentHashMap<>();

    @AfterEach void close() {
        if (server != null) server.stop(0);
        httpWorkers.shutdownNow();
    }

    @Test void retriesReal503TwiceThenParsesARealHttpResponse() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> reply(exchange, n < 3 ? 503 : 200, n < 3 ? "busy" : completion("正常")));
        assertThat(model.call("查一下版本")).isEqualTo("正常");
        assertThat(requests).hasValue(3);
        assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_CALL_RETRYING")).hasSize(2);
        assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_RESPONSE_VERIFIED")).hasSize(1);
    }

    @Test void succeedsOnTheFifthPhysicalRequestWithoutSwitchingModels() throws Exception {
        var model = serve(20_000, 2, (exchange, n) -> reply(exchange, n < 5 ? 503 : 200,
                n < 5 ? "busy" : completion("第五次恢复")));
        assertThat(model.call("读取状态")).isEqualTo("第五次恢复");
        assertThat(requests).hasValue(5);
        assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_CALL_RETRYING")).hasSize(4);
        assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_REQUEST_ATTEMPT"))
                .allSatisfy(e -> {
                    assertThat(e.getPayload().get("requestedModel")).isEqualTo(MODEL);
                    assertThat(e.getPayload().get("maxAttempts")).isEqualTo(5);
                });
    }

    @Test void streamingResponseCanRecoverOnTheFifthRequestBeforeAnyContent() throws Exception {
        var model = serve(20_000, 2, (exchange, n) -> {
            if (n < 5) reply(exchange, 503, "busy");
            else sse(exchange, chunk("第五次流式恢复"), false);
        });
        var result = model.stream(new Prompt("检查")).collectList().block();
        assertThat(result.stream().map(r -> r.getResult().getOutput().getText()).reduce("", String::concat))
                .isEqualTo("第五次流式恢复");
        assertThat(requests).hasValue(5);
    }

    @Test void retriesRealReadTimeoutWithoutResettingTheRequestBudget() throws Exception {
        var model = serve(5_000, 1, (exchange, n) -> {
            if (n == 1) pause(1_400);
            reply(exchange, 200, completion("恢复"));
        });
        assertThat(model.call("读取状态")).isEqualTo("恢复");
        assertThat(requests).hasValue(2);
    }

    @Test void retriesStreaming503BeforeTheFirstChunk() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> {
            if (n < 3) reply(exchange, 503, "busy");
            else sse(exchange, chunk("已恢复"), false);
        });
        var result = model.stream(new Prompt("检查")).collectList().block();
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(r -> r.getResult().getOutput().getText()).reduce("", String::concat)).isEqualTo("已恢复");
        assertThat(requests).hasValue(3);
    }

    @Test void emptyIdentifiedStreamRetriesBeforeExposingAnyContent() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> sse(exchange, chunk(n == 1 ? "" : "恢复"), false));
        var result = model.stream(new Prompt("检查")).collectList().block();
        assertThat(result.stream().map(r -> r.getResult().getOutput().getText()).reduce("", String::concat)).isEqualTo("恢复");
        assertThat(requests).hasValue(2);
    }

    @Test void emptyNonStreamingAnswerRetriesWithinTheSameRequestBudget() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> reply(exchange, 200, completion(n == 1 ? "" : "恢复")));
        assertThat(model.call("检查")).isEqualTo("恢复");
        assertThat(requests).hasValue(2);
    }

    @Test void continuouslyArrivingToolArgumentsMustNotTimeoutDuringSdkAggregation() throws Exception {
        serve(10_000, 1, (exchange, n) -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                for (int i = 0; i < 12; i++) {
                    var function = new java.util.LinkedHashMap<String,Object>();
                    function.put("arguments", i == 0 ? "{\"value\":\"" : i == 11 ? "\"}" : "x");
                    if (i == 0) function.put("name", "slow_tool");
                    var call = new java.util.LinkedHashMap<String,Object>();
                    call.put("index", 0); call.put("function", function);
                    if (i == 0) { call.put("id", "call-slow"); call.put("type", "function"); }
                    String payload = com.alibaba.fastjson.JSON.toJSONString(Map.of("id", "stream-slow", "object", "chat.completion.chunk",
                            "created", 1, "model", MODEL, "choices", List.of(Map.of("index", 0, "delta", Map.of("tool_calls", List.of(call))))));
                    body.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8)); body.flush(); pause(180);
                }
                String done = com.alibaba.fastjson.JSON.toJSONString(Map.of("id", "stream-slow", "object", "chat.completion.chunk",
                        "created", 1, "model", MODEL, "choices", List.of(Map.of("index", 0, "delta", Map.of(), "finish_reason", "tool_calls"))));
                body.write(("data: " + done + "\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8)); body.flush();
            }
        });
        var request = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest(List.of(), MODEL, (Double) null, true);
        var result = api.chatCompletionStream(request).collectList().block(java.time.Duration.ofSeconds(12));
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).choices().get(0).delta().toolCalls().get(0).function().arguments())
                .isEqualTo("{\"value\":\"xxxxxxxxxx\"}");
        assertThat(requests).hasValue(1);
        assertThat(events).noneMatch(e -> e.getEventType().equals("MODEL_CALL_RETRYING"));
    }

    @Test void parallelRequestsHaveIndependentRetryBudgets() throws Exception {
        var counts = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>();
        var model = serve(10_000, 2, (exchange, n) -> {
            String body = requestBodies.get(exchange);
            int count = counts.computeIfAbsent(body, ignored -> new AtomicInteger()).incrementAndGet();
            reply(exchange, count < 3 ? 503 : 200, count < 3 ? "busy" : completion("完成"));
        });
        var callers = Executors.newFixedThreadPool(4);
        try {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(n -> (java.util.concurrent.Callable<String>) () -> model.call("检查任务 " + n)).toList();
            for (var result : callers.invokeAll(tasks)) assertThat(result.get(12, TimeUnit.SECONDS)).isEqualTo("完成");
            assertThat(requests).hasValue(24);
            assertThat(counts).hasSize(8);
            assertThat(counts.values()).allSatisfy(value -> assertThat(value).hasValue(3));
        } finally { callers.shutdownNow(); }
    }

    @Test void emptySuccessfulHttpStreamRetriesOnlyBeforeAnyResponseWasReceived() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> sse(exchange, n < 3 ? "" : chunk("恢复后真实解析"), false));
        var result = model.stream(new Prompt("检查")).collectList().block();
        assertThat(result.stream().map(r -> r.getResult().getOutput().getText()).reduce("", String::concat))
                .isEqualTo("恢复后真实解析");
        assertThat(requests).hasValue(3);
        assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_CALL_RETRYING")).hasSize(2);
    }

    @Test void emptyRolePreambleCanPrecedeTheVerifiedModelWithoutExposingIt() throws Exception {
        var model = serve(5_000, 2, (exchange, n) -> sse(exchange,
                chunk("").replace("\"model\":\"" + MODEL + "\",", "") + chunk("已核验"), false));
        var result = model.stream(new Prompt("检查")).collectList().block();
        assertThat(result.stream().map(r -> r.getResult().getOutput().getText()).reduce("", String::concat)).isEqualTo("已核验");
        assertThat(requests).hasValue(1);
    }

    @Test void contentWithoutModelIdentityIsNeverDeliveredOrRetried() throws Exception {
        serve(5_000, 2, (exchange, n) -> sse(exchange,
                chunk("不得交给用户或工具执行器").replace("\"model\":\"" + MODEL + "\",", ""), false));
        var received = new CopyOnWriteArrayList<Object>();
        var request = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest(List.of(), MODEL, (Double) null, true);
        assertThatThrownBy(() -> api.chatCompletionStream(request).doOnNext(received::add).blockLast())
                .hasMessage("MODEL_RESPONSE_IDENTITY_MISSING");
        assertThat(received).isEmpty();
        assertThat(requests).hasValue(1);
        assertThat(events).noneMatch(e -> e.getEventType().equals("MODEL_CALL_RETRYING"));
    }

    @Test void streamingQuotaAndWrongIdentityNeverRetry() throws Exception {
        var model = serve(5_000, 2, (exchange, n) -> {
            if (n == 1) reply(exchange, 429, "{\"error\":{\"code\":\"insufficient_quota\"}}");
            else sse(exchange, chunk("结果").replace(MODEL, "unexpected-provider-model"), false);
        });
        assertThatThrownBy(() -> model.stream(new Prompt("检查")).blockLast()).hasMessage("MODEL_PROVIDER_QUOTA_EXHAUSTED");
        assertThatThrownBy(() -> model.stream(new Prompt("检查")).blockLast()).hasMessage("MODEL_RESPONSE_IDENTITY_MISMATCH");
        assertThat(requests).hasValue(2);
    }

    @Test void partialStreamingResponseIsNotReplayed() throws Exception {
        serve(5_000, 1, (exchange, n) -> sse(exchange, chunk("已收到的文字"), true));
        var seen = new CopyOnWriteArrayList<String>();
        var request = new org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest(List.of(), MODEL, (Double) null, true);
        assertThatThrownBy(() -> api.chatCompletionStream(request)
                .doOnNext(r -> seen.add(r.choices().get(0).delta().content())).blockLast())
                .isInstanceOf(RuntimeException.class);
        assertThat(requests).hasValue(1);
        assertThat(seen).contains("已收到的文字");
        assertThat(events).noneMatch(e -> e.getEventType().equals("MODEL_CALL_RETRYING"));
    }

    @Test void continuousSseChunksCannotExtendTheAbsoluteDeadline() throws Exception {
        var model = serve(500, 2, (exchange, n) -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                for (int i = 0; i < 30; i++) {
                    body.write(chunk("片段").getBytes(StandardCharsets.UTF_8)); body.flush(); pause(80);
                }
            }
        });
        long before = System.nanoTime();
        assertThatThrownBy(() -> model.stream(new Prompt("检查")).blockLast()).hasMessageContaining("MODEL_CALL_DEADLINE_EXHAUSTED");
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(1_500);
        assertThat(requests).hasValue(1);
    }

    @Test void authAndQuotaFailuresAndInvalidRequestsAreNotRetried() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> {
            int status = n == 1 ? 401 : n == 2 ? 429 : 400;
            reply(exchange, status, n == 2 ? "{\"error\":{\"code\":\"insufficient_quota\"}}" : "rejected");
        });
        assertThatThrownBy(() -> model.call("检查")).hasMessage("MODEL_PROVIDER_AUTH_FAILED");
        assertThatThrownBy(() -> model.call("检查")).hasMessage("MODEL_PROVIDER_QUOTA_EXHAUSTED");
        assertThatThrownBy(() -> model.call("检查")).hasMessage("MODEL_PROVIDER_REQUEST_REJECTED");
        assertThat(requests).hasValue(3);
        assertThat(events).noneMatch(e -> e.getEventType().equals("MODEL_CALL_RETRYING"));
    }

    @Test void wrongModelIdentityIsRejectedWithoutFallbackOrRetry() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> reply(exchange, 200, completion("结果").replace(MODEL, "unexpected-provider-model")));
        assertThatThrownBy(() -> model.call("检查")).hasMessage("MODEL_RESPONSE_IDENTITY_MISMATCH");
        assertThat(requests).hasValue(1);
    }

    @Test void persistentFailureIsBoundedToFivePhysicalRequests() throws Exception {
        var model = serve(20_000, 2, (exchange, n) -> reply(exchange, 502, "busy"));
        assertThatThrownBy(() -> model.call("检查")).hasMessage("MODEL_PROVIDER_UNAVAILABLE");
        assertThat(requests).hasValue(5);
        assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_CALL_RETRYING")).hasSize(4);
    }

    @Test void retryAfterBeyondTheRemainingDeadlineDoesNotDispatchAgain() throws Exception {
        var model = serve(2_000, 2, (exchange, n) -> {
            exchange.getResponseHeaders().set("Retry-After", "120"); reply(exchange, 429, "busy");
        });
        assertThatThrownBy(() -> model.call("检查")).hasMessage("MODEL_PROVIDER_RATE_LIMITED");
        assertThat(requests).hasValue(1);
    }

    @Test void cancellationDuringBackoffDoesNotMakeAnotherRequest() throws Exception {
        var model = serve(10_000, 2, (exchange, n) -> {
            exchange.getResponseHeaders().set("Retry-After", "5"); reply(exchange, 429, "busy");
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> model.call("检查"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (events.stream().noneMatch(e -> e.getEventType().equals("MODEL_CALL_RETRYING")) && System.nanoTime() < deadline) pause(10);
            assertThat(events).anyMatch(e -> e.getEventType().equals("MODEL_CALL_RETRYING"));
            future.cancel(true);
            executor.shutdown();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasValue(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void nodeDeadlineBoundsTheEntireRetrySequence() throws Exception {
        var model = serve(30_000, 2, (exchange, n) -> reply(exchange, 503, "busy"));
        long before = System.nanoTime();
        assertThatThrownBy(() -> OpsNodeDeadlineContext.withTimeout(1, () -> model.call("检查"))).isInstanceOf(RuntimeException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(1_400);
        assertThat(requests.get()).isLessThanOrEqualTo(2);
        assertThat(OpsNodeDeadlineContext.captureDeadline()).isNull();
    }

    @Test void aModelFailureAfterAToolDoesNotExecuteThatToolAgain() throws Exception {
        var toolExecutions = new AtomicInteger();
        var model = serve(10_000, 2, (exchange, n) -> {
            String response = n == 1 ? "{\"id\":\"fixture-tool\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"" + MODEL
                    + "\",\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call-one\",\"type\":\"function\",\"function\":{\"name\":\"record_once\",\"arguments\":\"{}\"}}]}}]}"
                    : n < 4 ? "busy" : completion("操作结果已复用");
            reply(exchange, n == 2 || n == 3 ? 503 : 200, response);
        });
        var tool = FunctionToolCallback.builder("record_once", (Map input) -> { toolExecutions.incrementAndGet(); return "receipt-one"; })
                .description("Records a fixture operation once").inputType(Map.class).build();
        String result = ChatClient.builder(model).defaultToolCallbacks(tool).build().prompt().user("处理").call().content();
        assertThat(result).isEqualTo("操作结果已复用");
        assertThat(requests).hasValue(4);
        assertThat(toolExecutions).hasValue(1);
    }

    private OpenAiChatModel serve(long deadlineMs, int readSeconds, Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(httpWorkers);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                requestBodies.put(exchange, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                handler.handle(exchange, requests.incrementAndGet());
            }
            catch (IOException ignored) { exchange.close(); }
            finally { requestBodies.remove(exchange); }
        });
        server.start();
        api = new OpsResilientOpenAiApi("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-key",
                "/v1/chat/completions", "/v1/embeddings", 1, readSeconds, deadlineMs, events::add);
        return OpenAiChatModel.builder().openAiApi(api).retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .defaultOptions(OpenAiChatOptions.builder().model(MODEL).build()).build();
    }

    private static String completion(String text) {
        return "{\"id\":\"fixture\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"" + MODEL
                + "\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"" + text + "\"}}]}";
    }
    private static String chunk(String text) {
        return "data: {\"id\":\"fixture\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"" + MODEL
                + "\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + text + "\"}}]}\n\n";
    }
    private static void reply(HttpExchange exchange, int status, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var body = exchange.getResponseBody()) { body.write(bytes); }
    }
    private static void sse(HttpExchange exchange, String value, boolean truncate) throws IOException {
        byte[] bytes = (value + (truncate ? "" : "data: [DONE]\n\n")).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length + (truncate ? 10000 : 0));
        try (var body = exchange.getResponseBody()) { body.write(bytes); body.flush(); if (truncate) pause(100); }
    }
    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange, int number) throws IOException; }
}
