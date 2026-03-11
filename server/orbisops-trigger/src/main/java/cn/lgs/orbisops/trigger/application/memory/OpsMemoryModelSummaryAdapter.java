package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryModelSummaryPort;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring AI adapter for optional context compression summaries. */
@Component
public class OpsMemoryModelSummaryAdapter implements MemoryModelSummaryPort {

    private final OpsMemoryChatModelResolver modelResolver;

    public OpsMemoryModelSummaryAdapter() {
        this(new OpsMemoryChatModelResolver(null, null, null));
    }

    @Autowired
    public OpsMemoryModelSummaryAdapter(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<ModelAvailabilityPort> aiModelAvailabilityProvider) {
        this(new OpsMemoryChatModelResolver(
                applicationContext,
                chatModelProvider,
                aiModelAvailabilityProvider == null
                        ? null
                        : aiModelAvailabilityProvider.getIfAvailable()));
    }

    OpsMemoryModelSummaryAdapter(OpsMemoryChatModelResolver modelResolver) {
        this.modelResolver = modelResolver;
    }

    @Override
    public String summarize(List<ColdMemoryMessageSnapshot> messages, int maxInputChars) {
        ChatModel chatModel = modelResolver.resolve();
        if (messages == null
                || messages.isEmpty()
                || chatModel == null
                || !modelResolver.chatAvailable()) {
            return "";
        }
        String content = ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是运维 Agent 的会话记忆压缩器。请把超出短期窗口的历史对话压缩成后续排障仍有用的摘要。
                        要求：
                        1. 保留 traceId、orderId、接口、错误码、指标名、时间范围、用户偏好、已排除原因和已有结论。
                        2. 不要保留寒暄和无稳定价值的过程文本。
                        3. 不要编造原文没有的信息。
                        4. 输出中文 Markdown，控制在 800 字以内。
                        """)
                .build()
                .prompt()
                .user(abbreviate(transcript(messages), Math.max(1200, maxInputChars)))
                .call()
                .content();
        return abbreviate(content == null ? "" : content.trim(), 1600);
    }

    String transcript(List<ColdMemoryMessageSnapshot> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .filter(message -> message != null)
                .map(message -> value(message.createdAt())
                        + " " + value(message.role()) + ": " + value(message.content()))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
