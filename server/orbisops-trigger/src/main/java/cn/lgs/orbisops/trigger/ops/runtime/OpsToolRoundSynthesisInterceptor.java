package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpsToolRoundSynthesisInterceptor extends ModelInterceptor {

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
        ModelRequest synthesisRequest = ModelRequest.builder(request)
                .messages(messages)
                .tools(List.of())
                .dynamicToolCallbacks(List.of())
                .toolDescriptions(Map.of())
                .build();
        return handler.call(synthesisRequest);
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
        if ("Skill".equalsIgnoreCase(name)) return false;

        String responseData = response.responseData() == null ? "" : response.responseData();
        if (name.startsWith("mcp_tool_catalog_")) return false;
        return !(name.startsWith("project_mcp_")
                && (responseData.contains("\"remoteToolName\":\"openapi_list_operations\"")
                || responseData.contains("\"toolName\":\"openapi_list_operations\"")));
    }
}
