package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.fastjson.JSON;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class OpsMcpDiscoveryFrameworkTest {
    @Test void frameworkReusesUnchangedIndexButRefreshesContractsAndRebuildsChangedSummaries() {
        var index = org.mockito.Mockito.spy(new org.springaicommunity.tool.searcher.RegexToolSearcher());
        var search = new OpsMcpToolSearch(5, index);
        var original = indexedTool("metrics", "Read metrics", "service");
        search.disclose(List.of(original), List.of(new UserMessage("inspect")));
        var loaded = search.disclose(List.of(original), history("[\"metrics\"]"));
        assertThat(loaded.getToolCallbacks()).contains(original);
        var changedSchema = indexedTool("metrics", "Read metrics", "resourceId");
        var refreshed = search.disclose(List.of(changedSchema), history("[\"metrics\"]"));
        assertThat(refreshed.getToolCallbacks()).contains(changedSchema).doesNotContain(original);
        org.mockito.Mockito.verify(index, org.mockito.Mockito.times(1)).clearIndex(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(index, org.mockito.Mockito.times(1)).indexTool(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        search.disclose(List.of(indexedTool("metrics", "Changed capability", "resourceId")), history("[\"metrics\"]"));
        org.mockito.Mockito.verify(index, org.mockito.Mockito.times(2)).clearIndex(org.mockito.ArgumentMatchers.anyString());
        var removed = search.disclose(List.of(), history("[\"metrics\"]"));
        assertThat(names(removed.getToolCallbacks())).containsExactly("toolSearchTool");
        org.mockito.Mockito.verify(index, org.mockito.Mockito.times(3)).clearIndex(org.mockito.ArgumentMatchers.anyString());
    }

    @Test void newPolicyInstanceRestoresTheRunSelectionAndRejectsMixedTrustedScopes() {
        var saved = new java.util.concurrent.ConcurrentHashMap<cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.Scope,
                cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.Selection>();
        cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore store = (scope, proposed) -> saved.computeIfAbsent(scope, ignored -> proposed);
        var fixture = new Fixture(2, "a");
        var server = fixture.bundle.getMcpServers().get(0);
        server.setRunId("run"); server.setAgentId("agent"); server.setNodeId("node");
        var policy = new OpsMcpDiscoveryPolicy(20, 2000, 5, EncodingType.O200K_BASE, store);
        var first = policy.select(fixture.bundle, 2, "[]");
        var recovered = new OpsMcpDiscoveryPolicy(20, 2000, 5, EncodingType.O200K_BASE, store)
                .select(fixture.bundle, 30, "[]");
        assertThat(recovered).isEqualTo(first);
        server.setRunId("next-run");
        assertThat(policy.select(fixture.bundle, 30, "[]").mode()).isEqualTo(OpsMcpDiscoveryPolicy.Mode.SEARCH);
        fixture.bundle.setMcpServers(List.of(server, server.toBuilder().runId("foreign-run").build()));
        assertThatThrownBy(() -> policy.select(fixture.bundle, 30, "[]")).hasMessage("MCP_DISCOVERY_SCOPE_MISMATCH");
    }

    private static ToolCallback indexedTool(String name, String description, String field) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() { return ToolDefinition.builder().name(name).description(description)
                    .inputSchema(JSON.toJSONString(Map.of("type", "object", "properties", Map.of(field, Map.of("type", "string"))))).build(); }
            public String call(String input) { return "unused"; }
        };
    }

    @Test void eitherThresholdSwitchesTheCompleteAuthorizedDirectoryAndCountsRealTokens() {
        var policy = OpsMcpDiscoveryPolicy.defaults();
        assertThat(policy.select(20, "[]").mode()).isEqualTo(OpsMcpDiscoveryPolicy.Mode.SUMMARY);
        assertThat(policy.select(21, "[]").mode()).isEqualTo(OpsMcpDiscoveryPolicy.Mode.SEARCH);
        assertThat(policy.select(1, "完整描述预算测试 ".repeat(3000)).mode()).isEqualTo(OpsMcpDiscoveryPolicy.Mode.SEARCH);
        String text = "[{\"name\":\"metrics\",\"description\":\"查询监控和请求延迟\"}]";
        int tokens = policy.select(1, text).tokens();
        assertThat(new OpsMcpDiscoveryPolicy(20, tokens, 5, EncodingType.O200K_BASE).select(1, text).mode())
                .isEqualTo(OpsMcpDiscoveryPolicy.Mode.SUMMARY);
        assertThat(new OpsMcpDiscoveryPolicy(20, tokens - 1, 5, EncodingType.O200K_BASE).select(1, text).mode())
                .isEqualTo(OpsMcpDiscoveryPolicy.Mode.SEARCH);
    }

    @Test void actualGraphSearchesLoadsExecutesOnceAndRetainsTheOriginalBusinessRoundBudget() throws Exception {
        var fixture = new Fixture(21, "a");
        var offered = new ArrayList<List<ToolCallback>>();
        var model = new ChatModel() {
            public ToolCallingChatOptions getDefaultOptions() { return ToolCallingChatOptions.builder().build(); }
            public ChatResponse call(Prompt prompt) {
                var callbacks = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
                offered.add(List.copyOf(callbacks));
                int round = offered.size();
                if (round == 1) assertThat(names(callbacks)).containsExactly("toolSearchTool");
                if (round == 2) {
                    assertThat(names(callbacks)).hasSize(6).contains(fixture.alias("metric_0"));
                    assertThat(callbacks).anySatisfy(tool -> {
                        assertThat(tool.getToolDefinition().name()).isEqualTo(fixture.alias("metric_0"));
                        assertThat(tool.getToolDefinition().inputSchema()).contains("service", "required");
                    });
                }
                if (round == 3) assertThat(callbacks).isEmpty();
                var message = round <= 2 ? AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("call-" + round, "function", round == 1 ? "toolSearchTool" : fixture.alias("metric_0"),
                                round == 1 ? "{\"query\":\"metric\",\"maxResults\":99}" : "{\"service\":\"orders\"}"))).build()
                        : new AssistantMessage("observed healthy");
                return new ChatResponse(List.of(new Generation(message)));
            }
            public Flux<ChatResponse> stream(Prompt prompt) { return Flux.defer(() -> Flux.just(call(prompt))); }
        };
        var agent = ReactAgent.builder().name("discovery_graph").model(model).tools(fixture.bundle.getTools())
                .interceptors(fixture.interceptor, new OpsToolRoundSynthesisInterceptor(1)).build();
        agent.call("Inspect orders metrics");
        assertThat(fixture.businessCalls).hasValue(1);
        assertThat(fixture.loads).hasValue(5);
        assertThat(offered).hasSize(3);
        assertThat(fixture.bulkReads).hasValue(3);
    }

    @Test void smallCatalogIsTextNotCallableAndGrowsWithoutSwitchingAnActiveInvocation() {
        var fixture = new Fixture(2, "a");
        var first = fixture.prepare(List.of(new UserMessage("inspect")));
        assertThat(first.getSystemMessage().getText()).contains("Read metric 0", "Read metric 1");
        assertThat(first.getDynamicToolCallbacks()).isEmpty();
        assertThat(names(first.getOptions().getToolCallbacks())).containsExactly("enable_mcp_tool_a");
        fixture.definitions.set(definitions(30));
        var next = fixture.prepare(List.of(new UserMessage("inspect")));
        assertThat(next.getDynamicToolCallbacks()).isEmpty();
        assertThat(next.getSystemMessage().getText()).contains("Read metric 29");
    }

    @Test void changedDeletedAndRevokedDefinitionsNeverSurviveAFrameworkSearch() {
        var fixture = new Fixture(21, "a");
        var first = fixture.prepare(List.of(new UserMessage("inspect")));
        String result = first.getDynamicToolCallbacks().get(0).call("{\"query\":\"metric_0\"}");
        var history = history(result);
        var next = fixture.prepare(history);
        var selected = next.getDynamicToolCallbacks().stream().filter(t -> t.getToolDefinition().name().equals(fixture.alias("metric_0"))).findFirst().orElseThrow();
        fixture.definitions.set(List.of(definition("metric_0", "resourceId")));
        assertThatThrownBy(() -> selected.call("{\"service\":\"orders\"}")).isInstanceOf(SecurityException.class);
        assertThat(fixture.prepare(history).getDynamicToolCallbacks()).anySatisfy(tool ->
                assertThat(tool.getToolDefinition().inputSchema()).contains("resourceId"));
        fixture.definitions.set(List.of());
        assertThat(names(fixture.prepare(history).getDynamicToolCallbacks())).containsExactly("toolSearchTool");
        assertThat(fixture.businessCalls).hasValue(0);
    }

    @Test void productionSchemaOnlyDoesNotBecomeExecutableAndFailedSearchDoesNotBreakTheNextModelTurn() {
        var fixture = new Fixture(21, "a");
        fixture.loadStatus.set("SCHEMA_ONLY");
        var search = fixture.prepare(List.of(new UserMessage("prepare"))).getDynamicToolCallbacks().get(0);
        String response = search.call("{\"query\":\"metric_0\"}");
        assertThat(response).isEqualTo("[]");
        var next = fixture.prepare(history(response));
        assertThat(names(next.getDynamicToolCallbacks())).containsExactly("toolSearchTool");
        assertThat(next.getSystemMessage().getText()).contains("未授予执行权限", "inputSchema");
        assertThat(names(fixture.prepare(history("Error: query too long")).getDynamicToolCallbacks())).containsExactly("toolSearchTool");
        assertThat(fixture.businessCalls).hasValue(0);
    }

    @Test void thousandsOfDefinitionsAreBoundedAndIndexesDoNotCrossRunsOrServers() {
        for (int count : List.of(1000, 10000)) {
            var a = new Fixture(count, "a");
            var b = new Fixture(count, "b");
            var sa = a.prepare(List.of(new UserMessage("inspect"))).getDynamicToolCallbacks().get(0);
            var sb = b.prepare(List.of(new UserMessage("inspect"))).getDynamicToolCallbacks().get(0);
            var hits = JSON.parseArray(sa.call("{\"query\":\"metric\",\"maxResults\":99999}"), String.class);
            assertThat(hits).hasSize(5).allMatch(name -> !name.equals(b.alias("metric_0")));
            assertThat(names(b.prepare(history(JSON.toJSONString(hits))).getDynamicToolCallbacks())).containsExactly("toolSearchTool");
            assertThat(JSON.parseArray(sb.call("{\"query\":\"metric_0\"}"), String.class)).containsExactly(b.alias("metric_0"));
            assertThat(a.bulkReads).hasValue(1);
            assertThat(a.businessCalls).hasValue(0);
        }
    }

    @Test void regularExpressionInputIsLiteralAndCannotRunBacktrackingPrograms() {
        var fixture = new Fixture(21, "a");
        var search = fixture.prepare(List.of(new UserMessage("inspect"))).getDynamicToolCallbacks().get(0);
        assertThat(search.call("{\"query\":\"(metric+)+$\"}")).isEqualTo("[]");
        assertThatThrownBy(() -> search.call(JSON.toJSONString(Map.of("query", "x".repeat(121)))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void actualFrameworkModelViewPublishesTheSameShortQueryContractThatExecutionEnforces() {
        var fixture = new Fixture(21, "a");
        var prepared = fixture.prepare(List.of(new UserMessage("inspect")));
        var search = prepared.getDynamicToolCallbacks().stream()
                .filter(tool -> "toolSearchTool".equals(tool.getToolDefinition().name())).findFirst().orElseThrow();
        var schema = JSON.parseObject(search.getToolDefinition().inputSchema());
        var query = schema.getJSONObject("properties").getJSONObject("query");
        assertThat(query.getIntValue("minLength")).isEqualTo(1);
        assertThat(query.getIntValue("maxLength")).isEqualTo(80);
        assertThat(search.getToolDefinition().description()).contains("1 to 80");
        assertThat(JSON.parseArray(search.call("{\"query\":\"metric_0\"}"), String.class))
                .containsExactly(fixture.alias("metric_0"));
        assertThatThrownBy(() -> search.call(JSON.toJSONString(Map.of("query", "x".repeat(81)))))
                .isInstanceOf(RuntimeException.class);
        assertThat(fixture.businessCalls).hasValue(0);
    }

    private static List<Message> history(String result) { return List.of(new UserMessage("inspect"),
            ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("search-1", "toolSearchTool", result))).build()); }
    private static List<String> names(List<ToolCallback> tools) { return tools.stream().map(t -> t.getToolDefinition().name()).toList(); }
    private static List<Map<String,Object>> definitions(int count) { return IntStream.range(0,count).mapToObj(i -> definition("metric_" + i, "service")).toList(); }
    private static Map<String,Object> definition(String name, String field) { return Map.of("name", name,
            "description", "Read metric " + name.substring(7), "inputSchema", Map.of("type", "object", "properties", Map.of(field, Map.of("type", "string")), "required", List.of(field))); }
    private static final class Fixture {
        final AtomicInteger businessCalls = new AtomicInteger(), loads = new AtomicInteger(), bulkReads = new AtomicInteger();
        final AtomicReference<List<Map<String,Object>>> definitions;
        final AtomicReference<String> loadStatus = new AtomicReference<>("ACTIVE");
        final OpsRuntimeResourceBundle bundle;
        final OpsMcpDisclosureInterceptor interceptor;
        final String server;
        Fixture(int count, String server) {
            this.server = server;
            definitions = new AtomicReference<>(definitions(count));
            var loader = tool("enable_mcp_tool_" + server, input -> {
                loads.incrementAndGet();
                return JSON.toJSONString(Map.of("status", loadStatus.get(), "toolName", JSON.parseObject(input).getString("toolName")));
            });
            var dispatcher = tool("project_mcp_" + server, input -> { businessCalls.incrementAndGet(); return "observed healthy"; });
            bundle = OpsRuntimeResourceBundle.builder().projectId("project-" + server)
                    .mcpServers(List.of(OpsMcpServerConfig.builder().toolId(server).mcpId(server).name(server).build()))
                    .tools(List.of(loader, dispatcher)).mcpDefinitionsReader(config -> { bulkReads.incrementAndGet(); return definitions.get(); })
                    .mcpDefinitionReader((config,name) -> definitions.get().stream().filter(d -> name.equals(d.get("name"))).findFirst().orElse(Map.of())).build();
            interceptor = new OpsMcpDisclosureInterceptor(bundle);
        }
        String alias(String name) { return OpsMcpDisclosureInterceptor.alias(server, name); }
        ModelRequest prepare(List<Message> messages) {
            var request = ModelRequest.builder().messages(messages).tools(names(bundle.getTools()))
                    .options(ToolCallingChatOptions.builder().toolCallbacks(bundle.getTools()).build()).build();
            var result = new AtomicReference<ModelRequest>();
            interceptor.interceptModel(request, next -> { result.set(next); return ModelResponse.of(new AssistantMessage("prepared")); });
            return result.get();
        }
        ToolCallback tool(String name, java.util.function.Function<String,String> action) {
            return new ToolCallback() {
                public ToolDefinition getToolDefinition() { return ToolDefinition.builder().name(name).description("fixture")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build(); }
                public String call(String input) { return action.apply(input); }
            };
        }
    }
}
