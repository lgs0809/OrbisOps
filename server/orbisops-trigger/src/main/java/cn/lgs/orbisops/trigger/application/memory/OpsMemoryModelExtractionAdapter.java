package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryModelExtractionPort;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spring AI adapter that converts model JSON into raw extraction drafts. */
@Component
public class OpsMemoryModelExtractionAdapter implements MemoryModelExtractionPort {

    private final OpsMemoryChatModelResolver modelResolver;

    public OpsMemoryModelExtractionAdapter() {
        this(new OpsMemoryChatModelResolver(null, null, null));
    }

    @Autowired
    public OpsMemoryModelExtractionAdapter(
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

    OpsMemoryModelExtractionAdapter(OpsMemoryChatModelResolver modelResolver) {
        this.modelResolver = modelResolver;
    }

    @Override
    public List<MemoryExtractionDraft> extract(ColdMemoryMessageSnapshot message, int maxInputChars) {
        ChatModel chatModel = modelResolver.resolve();
        if (message == null
                || chatModel == null
                || !modelResolver.chatAvailable()) {
            return List.of();
        }
        String content = ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是运维 Agent 的上下文记忆抽取器。Memory 只保存用户或项目的长期语境，不保存一次性排障过程。
                        要求：
                        1. 只允许 memoryType 为 USER_PREFERENCE、USER_WORKFLOW、USER_DOMAIN_FOCUS、PROJECT_CONTEXT、PROJECT_CONVENTION、PROJECT_GLOSSARY。
                        2. 不要保存 traceId、orderId、URL、错误码、指标值、临时排查步骤、工具调用结果，这些属于 Task Context / Trace。
                        3. 不要保存 SOP 排障方法，长期方法应该沉淀为 Skill。
                        4. 不要保存权限、审批、沙箱、执行中心、HTTP status、审计、脱敏等硬策略，硬策略属于 Policy。
                        5. USER_PREFERENCE / USER_WORKFLOW / USER_DOMAIN_FOCUS 只保存明确具有跨会话稳定性的表达，例如“以后/默认/每次/长期/通常/习惯/偏好/请记住”。一次性命令中的“不要、先别、帮我、希望、优先查、这次、当前”不是长期偏好；不得把本轮问题、问题重写或某次操作要求改写成“用户偏好”。
                        6. 无法稳定归类就返回空数组，不要编造原文没有的信息。
                        7. 只输出 JSON：{"memories":[{"scopeType":"USER|PROJECT","memoryType":"...","title":"...","summary":"...","content":"...","confidence":0.0,"reason":"...","tags":["..."]}]}。
                        8. memories 最多 6 条，content 使用中文短句。
                        """)
                .build()
                .prompt()
                .user(payload(message, maxInputChars))
                .call()
                .content();
        return decode(content);
    }

    List<MemoryExtractionDraft> decode(String content) {
        JSONObject parsed = parseJsonObject(content);
        JSONArray memories = parsed == null ? null : parsed.getJSONArray("memories");
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        List<MemoryExtractionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject memory = memories.getJSONObject(i);
            if (memory == null || !StringUtils.hasText(memory.getString("content"))) {
                continue;
            }
            List<String> tags = parseTags(memory.getJSONArray("tags"));
            drafts.add(new MemoryExtractionDraft(
                    text(memory.getString("memoryType"), memory.getString("type")),
                    abbreviate(memory.getString("content").trim(), 320),
                    memory.getBigDecimal("confidence") == null
                            ? memory.getBigDecimal("importance")
                            : memory.getBigDecimal("confidence"),
                    JSON.toJSONString(tags.isEmpty() ? List.of("llm") : tags),
                    "llm_extractor",
                    memory.getString("scopeType"),
                    memory.getString("title"),
                    memory.getString("summary"),
                    memory.getString("reason")));
        }
        return List.copyOf(drafts);
    }

    private String payload(ColdMemoryMessageSnapshot message, int maxInputChars) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role());
        payload.put("sessionId", message.sessionId());
        payload.put("userId", message.userId());
        payload.put("metadata", message.metadata());
        payload.put("content", abbreviate(message.content(), Math.max(800, maxInputChars)));
        return JSON.toJSONString(payload);
    }

    private JSONObject parseJsonObject(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return JSON.parseObject(text.substring(start, end + 1));
    }

    private List<String> parseTags(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String tag = array.getString(i);
            if (StringUtils.hasText(tag)) {
                tags.add(tag.trim());
            }
        }
        return tags.stream().distinct().limit(6).toList();
    }

    private String text(Object value, Object fallback) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return String.valueOf(value).trim();
        }
        return fallback == null ? "" : String.valueOf(fallback).trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }
}
