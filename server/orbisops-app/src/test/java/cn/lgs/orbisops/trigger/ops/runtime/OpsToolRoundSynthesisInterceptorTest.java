package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpsToolRoundSynthesisInterceptorTest {

    @Test
    void shouldRemoveToolsAndForceFinalSynthesisAfterConfiguredRounds() {
        OpsToolRoundSynthesisInterceptor interceptor = new OpsToolRoundSynthesisInterceptor(2);
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(
                        new UserMessage("check service"),
                        ToolResponseMessage.builder().responses(List.of()).build(),
                        ToolResponseMessage.builder().responses(List.of()).build()))
                .tools(List.of("prometheus_query"))
                .toolDescriptions(Map.of("prometheus_query", "query metrics"))
                .build();
        AtomicReference<ModelRequest> captured = new AtomicReference<>();

        interceptor.interceptModel(request, intercepted -> {
            captured.set(intercepted);
            return ModelResponse.of(new AssistantMessage("final"));
        });

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getTools()).isEmpty();
        assertThat(captured.get().getDynamicToolCallbacks()).isEmpty();
        assertThat(captured.get().getToolDescriptions()).isEmpty();
        assertThat(captured.get().getMessages().get(captured.get().getMessages().size() - 1).getText())
                .contains("不要再调用任何工具")
                .contains("不能输出 null")
                .contains("不豁免 System Prompt 中的最终机器协议")
                .contains("<ops_outcome>");
    }

    @Test
    void shouldNotConsumeInvestigationBudgetForSkillAndOpenApiDiscovery() {
        OpsToolRoundSynthesisInterceptor interceptor = new OpsToolRoundSynthesisInterceptor(2);
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(
                        new UserMessage("check service"),
                        toolResponse("Skill", "loaded"),
                        toolResponse(
                                "project_mcp_demo_openapi_prod_readonly_mcp",
                                "{\"remoteToolName\":\"openapi_list_operations\"}"),
                        toolResponse("prometheus_query", "metric evidence")))
                .tools(List.of("prometheus_query", "elasticsearch_search"))
                .toolDescriptions(Map.of(
                        "prometheus_query", "query metrics",
                        "elasticsearch_search", "query logs"))
                .build();
        AtomicReference<ModelRequest> captured = new AtomicReference<>();

        interceptor.interceptModel(request, intercepted -> {
            captured.set(intercepted);
            return ModelResponse.of(new AssistantMessage("continue"));
        });

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getTools())
                .containsExactly("prometheus_query", "elasticsearch_search");
    }

    @Test
    void shouldStillCountDatasourceRoundsAfterDiscoveryCalls() {
        OpsToolRoundSynthesisInterceptor interceptor = new OpsToolRoundSynthesisInterceptor(2);
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(
                        new UserMessage("check service"),
                        toolResponse("Skill", "loaded"),
                        toolResponse(
                                "project_mcp_demo_openapi_prod_readonly_mcp",
                                "{\"toolName\":\"openapi_list_operations\"}"),
                        toolResponse("prometheus_query", "metric evidence"),
                        toolResponse("elasticsearch_search", "log evidence")))
                .tools(List.of("prometheus_query", "elasticsearch_search"))
                .toolDescriptions(Map.of(
                        "prometheus_query", "query metrics",
                        "elasticsearch_search", "query logs"))
                .build();
        AtomicReference<ModelRequest> captured = new AtomicReference<>();

        interceptor.interceptModel(request, intercepted -> {
            captured.set(intercepted);
            return ModelResponse.of(new AssistantMessage("final"));
        });

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getTools()).isEmpty();
    }

    private ToolResponseMessage toolResponse(String name, String responseData) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "tool-call-id", name, responseData)))
                .build();
    }
}
