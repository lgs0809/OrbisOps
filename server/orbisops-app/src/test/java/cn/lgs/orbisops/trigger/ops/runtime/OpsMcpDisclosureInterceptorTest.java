package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpsMcpDisclosureInterceptorTest {
    private final AtomicInteger businessCalls = new AtomicInteger();
    private final AtomicReference<Map<String,Object>> current = new AtomicReference<>(definition("service"));
    private final ToolCallback loader = callback("enable_mcp_tool_server_a", "{\"status\":\"ACTIVE\",\"toolName\":\"read_state\"}", false);
    private final ToolCallback dispatcher = callback("project_mcp_server_a", "observed", true);

    @Test void schemaReplacementDeletionRevocationAndStaleArgumentsAreEnforcedBeforeDispatch() {
        var interceptor = new OpsMcpDisclosureInterceptor(bundle(List.of(loader, dispatcher)));
        var request = request("ACTIVE");
        ToolCallback first = dynamic(interceptor, request).get(0);
        assertThat(first.getToolDefinition().inputSchema()).contains("service");
        current.set(definition("resourceId"));
        assertThatThrownBy(() -> first.call("{\"service\":\"orders\"}"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("CHANGED_REPLAN");
        assertThat(businessCalls).hasValue(0);
        var refreshed = dynamic(interceptor, request).get(0);
        assertThat(refreshed.getToolDefinition().inputSchema()).contains("resourceId").doesNotContain("service");
        refreshed.call("{\"resourceId\":\"orders\"}");
        assertThat(businessCalls).hasValue(1);
        current.set(Map.of());
        assertThat(dynamic(interceptor, request)).isEmpty();
        assertThat(dynamic(interceptor, ModelRequest.builder(request).dynamicToolCallbacks(List.of(refreshed)).build())).isEmpty();
        assertThatThrownBy(() -> refreshed.call("{}" )).isInstanceOf(SecurityException.class);
        assertThat(businessCalls).hasValue(1);
    }

    @Test void schemaOnlyAndHistoricalLoadingCannotBypassThePostAuthorityBundle() {
        assertThat(dynamic(new OpsMcpDisclosureInterceptor(bundle(List.of(loader, dispatcher))), request("SCHEMA_ONLY"))).isEmpty();
        assertThat(dynamic(new OpsMcpDisclosureInterceptor(bundle(List.of(loader))), request("ACTIVE"))).isEmpty();
        assertThat(dynamic(new OpsMcpDisclosureInterceptor(bundle(List.of(dispatcher))), request("ACTIVE"))).isEmpty();
        assertThat(OpsMcpDisclosureInterceptor.alias("a", "same"))
                .isNotEqualTo(OpsMcpDisclosureInterceptor.alias("b", "same"));
    }

    @Test void actualAlibabaGraphReceivesTheSchemaNextRoundAndCallsTheExistingDispatcherExactlyOnce() throws Exception {
        var bundle = bundle(List.of(loader, dispatcher));
        var model = mock(ChatModel.class);
        List<List<String>> offered = new ArrayList<>();
        java.util.function.Function<Prompt,ChatResponse> answer = prompt -> {
            var tools = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
            offered.add(tools.stream().map(t -> t.getToolDefinition().name()).toList());
            int round = offered.size();
            String name = round == 1 ? loader.getToolDefinition().name() : OpsMcpDisclosureInterceptor.alias("server-a", "read_state");
            if (round == 1) assertThat(offered.get(0)).doesNotContain(nameForRead());
            if (round == 2) assertThat(tools).anySatisfy(tool -> {
                assertThat(tool.getToolDefinition().name()).isEqualTo(nameForRead());
                assertThat(tool.getToolDefinition().inputSchema()).contains("required", "service");
            });
            var message = round <= 2 ? AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(
                    "call-" + round, "function", name, round == 1 ? "{\"toolName\":\"read_state\"}" : "{\"service\":\"orders\"}"))).build()
                    : new AssistantMessage("verified");
            return new ChatResponse(List.of(new Generation(message)));
        };
        when(model.call(any(Prompt.class))).thenAnswer(call -> answer.apply(call.getArgument(0)));
        when(model.stream(any(Prompt.class))).thenAnswer(call -> Flux.just(answer.apply(call.getArgument(0))));
        var agent = ReactAgent.builder().name("disclosure_test").model(model).tools(bundle.getTools())
                .interceptors(new OpsMcpDisclosureInterceptor(bundle)).build();
        agent.call("Inspect orders");
        assertThat(businessCalls).hasValue(1);
        assertThat(offered).hasSize(3);
    }

    @Test void nativeDispatchPreservesCallIdentityWithoutMutatingOriginalHistory() {
        var received = new AtomicReference<ToolContext>();
        var delegated = new ToolCallback() {
            public ToolDefinition getToolDefinition() { return dispatcher.getToolDefinition(); }
            public String call(String input) { throw new AssertionError("Context must be retained"); }
            public String call(String input, ToolContext context) { received.set(context); return "observed"; }
        };
        var callback = dynamic(new OpsMcpDisclosureInterceptor(bundle(List.of(loader, delegated))), request("ACTIVE")).get(0);
        String input = "{\"service\":\"orders\"}";
        var original = new ToolContext(Map.of(ToolContext.TOOL_CALL_HISTORY, List.of(
                AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(
                        "native-call-123", "function", callback.getToolDefinition().name(), input))).build()), "tenant", "a"));
        callback.call(input, original);
        var identity = (OpsMcpDisclosureInterceptor.NativeInvocation) received.get().getContext()
                .get(OpsMcpDisclosureInterceptor.NATIVE_INVOCATION);
        assertThat(identity.callId()).isEqualTo("native-call-123");
        assertThat(identity.dispatcher()).isEqualTo("project_mcp_server_a");
        assertThat(identity.routedInput()).contains("read_state", "arguments");
        assertThat(original.getContext()).doesNotContainKey(OpsMcpDisclosureInterceptor.NATIVE_INVOCATION);
        assertThat(received.get().getToolCallHistory()).isEqualTo(original.getToolCallHistory());
        assertThat(received.get().getContext()).containsEntry("tenant", "a");
    }

    private String nameForRead() { return OpsMcpDisclosureInterceptor.alias("server-a", "read_state"); }
    private OpsRuntimeResourceBundle bundle(List<ToolCallback> tools) {
        return OpsRuntimeResourceBundle.builder().mcpServers(List.of(OpsMcpServerConfig.builder()
                .name("server-a").mcpId("server-a").toolId("server-a").allowedTools(List.of("read_state")).build())).tools(tools)
                .mcpDefinitionReader((server,name) -> current.get()).build();
    }
    private ModelRequest request(String status) {
        return ModelRequest.builder().messages(List.of(new UserMessage("inspect"), ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("call", loader.getToolDefinition().name(),
                        "{\"status\":\"" + status + "\",\"toolName\":\"read_state\"}"))).build()))
                .tools(List.of(loader.getToolDefinition().name(), dispatcher.getToolDefinition().name())).build();
    }
    private List<ToolCallback> dynamic(OpsMcpDisclosureInterceptor interceptor, ModelRequest request) {
        var captured = new AtomicReference<ModelRequest>();
        interceptor.interceptModel(request, next -> { captured.set(next); return ModelResponse.of(new AssistantMessage("next")); });
        return captured.get().getDynamicToolCallbacks();
    }
    private static Map<String,Object> definition(String property) {
        return Map.of("name", "read_state", "description", "Read current state", "inputSchema", Map.of(
                "type", "object", "properties", Map.of(property, Map.of("type", "string")), "required", List.of(property)));
    }
    private ToolCallback callback(String name, String output, boolean business) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() { return ToolDefinition.builder().name(name).description("fixture")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}").build(); }
            public String call(String input) {
                if (business) { assertThat(input).contains("read_state", "arguments"); businessCalls.incrementAndGet(); }
                return output;
            }
        };
    }
}
