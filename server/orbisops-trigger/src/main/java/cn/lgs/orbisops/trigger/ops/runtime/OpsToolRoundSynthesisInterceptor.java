package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpsToolRoundSynthesisInterceptor extends ModelInterceptor {

    private static final String PREPARE_CHANGE_PACKAGE = "PrepareChangePackage";
    private final int maxToolRounds;

    OpsToolRoundSynthesisInterceptor(int maxToolRounds) {
        this.maxToolRounds = Math.max(1, maxToolRounds);
    }

    @Override
    public String getName() {
        return "ops-tool-round-synthesis";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String preparedOutput = preparedChangePackageOutput(request.getMessages());
        if (preparedOutput != null) {
            // PrepareChangePackage is the terminal transition of the investigation phase. Once
            // the governed tool has returned, asking the model to choose another tool can only
            // repeat evidence collection (or create a second proposal) and leaves a valid package
            // without a chat response. Synthesize the short hand-off locally from the authoritative
            // tool observation; the proposal itself remains durable in the ChangePackage store.
            return ModelResponse.of(new AssistantMessage(preparedOutput));
        }
        long completedToolRounds = request.getMessages().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .filter(this::consumesInvestigationBudget)
                .count();
        if (completedToolRounds < maxToolRounds) {
            return handler.call(request);
        }

        List<Message> messages = new ArrayList<>(request.getMessages());
        messages.add(new UserMessage("""
                工具调查轮次已达到上限。不要再调用任何工具。
                请仅基于上文已有的工具 observation 生成最终回答，明确列出结论、实际证据、证据缺口和建议。
                若已有 observation 不足，必须说明不足，不能输出 null、空答案或编造数据。
                工具轮次耗尽只终止继续调用工具，不豁免 System Prompt 中的最终机器协议；若 System Prompt 要求 <ops_outcome> 等 typed completion，最终回答仍必须完整输出且保持字段语义一致。
                """));
        org.springframework.ai.model.tool.ToolCallingChatOptions options = request.getOptions() == null
                ? org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build() : request.getOptions().copy();
        options.setToolCallbacks(List.of());
        options.setToolNames(java.util.Set.of());
        ModelRequest synthesisRequest = ModelRequest.builder(request)
                .options(options)
                .messages(messages)
                .tools(List.of())
                .dynamicToolCallbacks(List.of())
                .toolDescriptions(Map.of())
                .build();
        return handler.call(synthesisRequest);
    }

    private String preparedChangePackageOutput(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (!(message instanceof ToolResponseMessage toolResponse)
                    || toolResponse.getResponses() == null) {
                continue;
            }
            for (int responseIndex = toolResponse.getResponses().size() - 1;
                 responseIndex >= 0;
                 responseIndex--) {
                ToolResponseMessage.ToolResponse response = toolResponse.getResponses().get(responseIndex);
                if (response != null
                        && PREPARE_CHANGE_PACKAGE.equalsIgnoreCase(response.name())) {
                    return OpsPreparedChangePackageSummary.renderReviewReady(response.responseData());
                }
            }
        }
        return null;
    }

    /**
     * Capability/resource discovery prepares the investigation context and must not consume the
     * bounded evidence-gathering budget. Datasource, RAG, code, validation and action tools still
     * count normally, so the guard remains fail-safe against unbounded ReAct loops.
     */
    private boolean consumesInvestigationBudget(ToolResponseMessage message) {
        if (message == null || message.getResponses() == null || message.getResponses().isEmpty()) {
            return true;
        }
        return message.getResponses().stream().anyMatch(this::consumesInvestigationBudget);
    }

    private boolean consumesInvestigationBudget(ToolResponseMessage.ToolResponse response) {
        if (response == null) return true;
        String name = response.name() == null ? "" : response.name().trim();
        if ("toolSearchTool".equals(name)) return false;
        if ("Skill".equalsIgnoreCase(name) || "UseProjectSkill".equalsIgnoreCase(name)) return false;

        String responseData = response.responseData() == null ? "" : response.responseData();
        // Loading a reviewed schema only prepares a subsequent business call. Counting it as
        // evidence gathering exhausted an investigation before the proposal could be
        // saved. The enclosing graph recursion/deadline still bounds discovery and loading.
        if (name.startsWith("mcp_tool_catalog_") || name.startsWith("enable_mcp_tool_")) return false;
        return !(name.startsWith("project_mcp_")
                && (responseData.contains("\"remoteToolName\":\"openapi_list_operations\"")
                || responseData.contains("\"toolName\":\"openapi_list_operations\"")));
    }
}
