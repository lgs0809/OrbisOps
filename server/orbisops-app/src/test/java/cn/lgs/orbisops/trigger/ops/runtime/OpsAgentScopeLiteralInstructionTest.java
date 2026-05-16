package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpsAgentScopeLiteralInstructionTest {
    @Test void assembledJsonInstructionReachesModelWithoutTemplateEvaluation() throws Exception {
        String literal = "只输出 JSON：{\"type\":\"object\",\"properties\":{\"service\":{\"type\":\"string\"}}}；保留 {unknownVariable}";
        var seen = new java.util.concurrent.CopyOnWriteArrayList<Prompt>();
        ChatModel model = mock(ChatModel.class);
        ChatResponse answer = new ChatResponse(List.of(new Generation(new AssistantMessage("已理解"))));
        when(model.call(any(Prompt.class))).thenAnswer(call -> {seen.add(call.getArgument(0)); return answer;});
        when(model.stream(any(Prompt.class))).thenAnswer(call -> {seen.add(call.getArgument(0)); return Flux.just(answer);});
        var factory = new OpsAgentScopePipelineFactory(new OpsRuntimePromptAssembler(), Runnable::run,
                new OpsAgentScopeConfigPolicy(), new OpsAgentScopeFlowPolicy());
        var definition = OpsAgentDefinition.builder().agentId("literal").instruction(literal).build();
        var prepared = factory.prepare(definition, OpsAgentChatRequest.builder().projectId("p").build(),
                List.of(OpsAgentScopeConfig.builder().agentId("resolver").instruction(literal).build()),
                List.of(OpsRuntimeResourceBundle.builder().projectId("p").chatModel(model).build()),
                new ArrayList<>(), null, mock(OpsAgentScopeExecutor.Hooks.class));
        ((ReactAgent) prepared.agents().get(0)).call("请检查服务 {orders}");
        assertFalse(seen.isEmpty());
        String messages = seen.get(0).getInstructions().stream().map(m -> m.getText()).reduce("", (a,b) -> a + "\n" + b);
        assertTrue(messages.contains(literal));
        assertTrue(messages.contains("{orders}"));
    }
}
