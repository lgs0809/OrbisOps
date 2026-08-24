package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpsToolRoundSynthesisInterceptorTest {

    @Test
    void terminatesInvestigationAfterPrepareChangePackageReturns() {
        OpsToolRoundSynthesisInterceptor interceptor = new OpsToolRoundSynthesisInterceptor(20);
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(
                        new UserMessage("prepare a safe proposal"),
                        toolResponse("PrepareChangePackage",
                                "{\"packageId\":\"cp-1\",\"status\":\"READY_FOR_REVIEW\",\"version\":1,\"packageHash\":\"" + "a".repeat(64) + "\"}")))
                .tools(List.of("PrepareChangePackage", "restart_service"))
                .toolDescriptions(Map.of(
                        "PrepareChangePackage", "prepare proposal",
                        "restart_service", "restart service"))
                .build();
        AtomicBoolean modelCalled = new AtomicBoolean();

        ModelResponse response = interceptor.interceptModel(request, intercepted -> {
            modelCalled.set(true);
            return ModelResponse.of(new AssistantMessage("unexpected"));
        });

        assertThat(response).isNotNull();
        assertThat(modelCalled).isFalse();
    }

    @Test
    void failedOrIncompletePrepareMustReturnToModelWithoutInventingSavedPackage() {
        for (String output : List.of("", "Error: CHANGE_PACKAGE_AUTHORITATIVE_EVIDENCE_REQUIRED",
                "{\"error\":\"failed\",\"message\":\"packageId: cp-fake\"}",
                "{\"packageId\":\"cp-fake\",\"status\":\"READY_FOR_REVIEW\"}")) {
            AtomicBoolean called = new AtomicBoolean();
            new OpsToolRoundSynthesisInterceptor(20).interceptModel(ModelRequest.builder()
                    .messages(List.of(new UserMessage("prepare"), toolResponse("PrepareChangePackage", output)))
                    .tools(List.of("PrepareChangePackage")).build(), request -> {
                        called.set(true);
                        return ModelResponse.of(new AssistantMessage("方案创建失败，继续检查证据"));
                    });
            assertThat(called).isTrue();
        }
    }

    @Test
    void failedValidationCandidateIsSavedButMustNotBePresentedAsReadyForApproval() {
        String output = "{\"packageId\":\"cp-1\",\"status\":\"VALIDATION_FAILED\",\"version\":1,\"packageHash\":\""
                + "a".repeat(64) + "\"}";
        assertThat(OpsPreparedChangePackageSummary.render(output)).contains("验证未通过", "当前不能审批落地")
                .doesNotContain("已创建审批方案", "未执行实际变更");
    }

    @Test
    void savedValidationFailureKeepsTheModelLoopAvailableForCorrection() {
        AtomicBoolean called = new AtomicBoolean();
        String output = "{\"packageId\":\"cp-1\",\"status\":\"VALIDATION_FAILED\",\"version\":1,\"packageHash\":\""
                + "a".repeat(64) + "\"}";
        new OpsToolRoundSynthesisInterceptor(20).interceptModel(ModelRequest.builder()
                .messages(List.of(new UserMessage("完善方案"), toolResponse("PrepareChangePackage", output)))
                .tools(List.of("PrepareChangePackage")).build(), request -> {
                    called.set(true);
                    assertThat(request.getTools()).contains("PrepareChangePackage");
                    return ModelResponse.of(new AssistantMessage("继续修正校验问题"));
                });
        assertThat(called).isTrue();
    }

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

    @Test
    void loadingSchemasLeavesBusinessBudgetButMixedBatchesAndFailuresStillCount() {
        var loading = toolResponse("enable_mcp_tool_project_a", "{\"status\":\"ACTIVE\"}");
        var read = toolResponse("project_mcp_project_a", "{\"status\":\"SUCCEEDED\"}");
        var failure = toolResponse("project_mcp_project_a", "{\"error\":\"TIMEOUT\"}");
        var mixed = ToolResponseMessage.builder().responses(List.of(
                loading.getResponses().get(0), read.getResponses().get(0))).build();

        // A prepared proposal remains callable after discovery and one actual business round.
        new OpsToolRoundSynthesisInterceptor(2).interceptModel(ModelRequest.builder()
                .messages(List.of(new UserMessage("调查后保存方案"), loading, loading,
                        toolResponse("UseProjectSkill", "method loaded"), read))
                .tools(List.of("PrepareChangePackage")).build(), request -> {
                    assertThat(request.getTools()).containsExactly("PrepareChangePackage");
                    return ModelResponse.of(new AssistantMessage("继续保存"));
                });

        // A loading response cannot hide a business action or a failed business attempt.
        new OpsToolRoundSynthesisInterceptor(2).interceptModel(ModelRequest.builder()
                .messages(List.of(new UserMessage("调查"), loading, mixed, failure))
                .tools(List.of("PrepareChangePackage")).build(), request -> {
                    assertThat(request.getTools()).isEmpty();
                    return ModelResponse.of(new AssistantMessage("预算已耗尽"));
                });
    }

    private ToolResponseMessage toolResponse(String name, String responseData) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "tool-call-id", name, responseData)))
                .build();
    }
}
