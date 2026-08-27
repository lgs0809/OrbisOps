package cn.lgs.orbisops.compat;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springaicommunity.tool.search.ToolSearchToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springaicommunity.tool.search.ToolReference;
import org.springaicommunity.tool.search.ToolSearchRequest;
import org.springaicommunity.tool.searcher.RegexToolSearcher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Framework execution only: a deterministic model oracle is not a real-LLM quality benchmark. */
class ToolSearchFrameworkCompatibilityTest {
    @Test
    void advisorInjectsFullSchemasAfterSearchAndExecutesOnlyTheSelectedCallback() {
        var run = fixture("plain-session");
        assertThat(run.client.prompt("inspect").call().content()).isEqualTo("inspection complete");
        verify(run);
    }

    @Test
    void alibabaReactAgentCanUseTheOfficialAdvisorWithoutDispatchingTwice() throws Exception {
        var run = fixture("graph-session");
        var agent = ReactAgent.builder().name("search_compat").chatClient(run.client)
                .instruction("Inspect the fixture through the tool search entry.")
                .interceptors(new com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor() {
                    public String getName() { return "instruction-message-compat"; }
                    public com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse interceptModel(
                            com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest request,
                            com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler handler) {
                        var messages = request.getMessages().stream().map(message ->
                                message instanceof com.alibaba.cloud.ai.graph.serializer.AgentInstructionMessage
                                    ? (org.springframework.ai.chat.messages.Message) org.springframework.ai.chat.messages.UserMessage.builder().text(message.getText()).metadata(message.getMetadata()).build()
                                    : message).toList();
                        return handler.call(com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest.builder(request)
                                .messages(messages).build());
                    }
                }).build();
        agent.call("inspect");
        verify(run);
    }

    @Test
    void capacityAndSessionIsolationAtOneThousandAndTenThousandDefinitions() {
        for (int count : List.of(1000, 10000)) {
            var index = new RegexToolSearcher();
            var definitions = IntStream.range(0, count)
                    .mapToObj(i -> new ToolReference("ops_metric_" + i, 1.0, "Read isolated metric " + i)).toList();
            definitions.forEach(ref -> index.indexTool("project-a", ref));
            index.indexTool("project-b", new ToolReference("private_backup", 1.0, "Restricted backup"));
            assertThat(index.search(new ToolSearchRequest("project-a", "ops_metric_", 5, null)).toolReferences())
                    .hasSize(5).allSatisfy(ref -> assertThat(ref.toolName()).startsWith("ops_metric_"));
            assertThat(index.search(new ToolSearchRequest("project-b", "ops_metric_", 5, null)).toolReferences()).isEmpty();
            index.clearIndex("project-a");
            assertThat(index.search(new ToolSearchRequest("project-a", "ops_metric_", 5, null)).toolReferences()).isEmpty();
            assertThat(index.search(new ToolSearchRequest("project-b", "private_backup", 5, null)).toolReferences()).hasSize(1);
        }
    }

    private void verify(Fixture run) {
        assertThat(run.calls).hasValue(1);
        assertThat(run.model.offered).hasSize(3);
        assertThat(run.model.offered.get(0)).containsExactly("toolSearchTool");
        assertThat(run.model.offered.get(1)).contains("toolSearchTool", "metric_0").hasSize(6);
        assertThat(run.model.schemas.get(1)).anySatisfy(schema -> assertThat(schema).contains("required", "service"));
    }

    private Fixture fixture(String session) {
        var calls = new AtomicInteger();
        List<ToolCallback> tools = IntStream.range(0, 21).mapToObj(i -> (ToolCallback) new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("metric_" + i).description("Read metric " + i)
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"service\":{\"type\":\"string\"}},\"required\":[\"service\"]}").build();
            }
            public String call(String input) {
                assertThat(i).isZero();
                assertThat(input).contains("orders");
                calls.incrementAndGet();
                return "observed healthy";
            }
        }).toList();
        var model = new Oracle();
        var advisor = ToolSearchToolCallAdvisor.builder().toolSearcher(new RegexToolSearcher()).maxResults(5).build();
        var client = ChatClient.builder(model).defaultToolCallbacks(tools)
                .defaultAdvisors(a -> a.advisors(advisor).param(ChatMemory.CONVERSATION_ID, session)).build();
        return new Fixture(model, client, calls);
    }

    private record Fixture(Oracle model, ChatClient client, AtomicInteger calls) { }

    private static final class Oracle implements ChatModel {
        final List<List<String>> offered = new ArrayList<>();
        final List<List<String>> schemas = new ArrayList<>();
        public ToolCallingChatOptions getDefaultOptions() { return ToolCallingChatOptions.builder().build(); }
        public ToolCallingChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
        public ChatResponse call(Prompt prompt) {
            var tools = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
            offered.add(tools.stream().map(t -> t.getToolDefinition().name()).toList());
            schemas.add(tools.stream().map(t -> t.getToolDefinition().inputSchema()).toList());
            AssistantMessage message = switch (offered.size()) {
                case 1 -> AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("search-1", "function", "toolSearchTool",
                                "{\"query\":\"metric_\",\"maxResults\":99,\"categoryFilter\":null}"))).build();
                case 2 -> AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("metric-1", "function", "metric_0", "{\"service\":\"orders\"}"))).build();
                default -> AssistantMessage.builder().content("inspection complete").build();
            };
            return new ChatResponse(List.of(new Generation(message)));
        }
        public Flux<ChatResponse> stream(Prompt prompt) { return Flux.defer(() -> Flux.just(call(prompt))); }
    }
}
