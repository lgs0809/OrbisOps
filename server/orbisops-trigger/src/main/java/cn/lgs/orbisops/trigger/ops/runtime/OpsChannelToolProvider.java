package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class OpsChannelToolProvider {

    private final OpsToolExecutionService toolExecutionService;
    private final ChannelQueryService channelQueryService;

    public OpsChannelToolProvider(OpsToolExecutionService toolExecutionService, ChannelQueryService channelQueryService) {
        this.toolExecutionService = toolExecutionService;
        this.channelQueryService = channelQueryService;
    }

    public boolean available(String projectId) {
        if (!StringUtils.hasText(projectId)) return false;
        try {
            return !channelQueryService.list(projectId).isEmpty();
        } catch (RuntimeException error) {
            log.warn("Channel 不可用，本次 Work Session 不暴露通知工具，projectId={} error={}", projectId, error.getMessage());
            return false;
        }
    }

    public ToolCallback build(String projectId, String actor, String runId) {
        Function<ChannelInput, String> function = input -> JSON.toJSONString(execute(projectId, actor, runId, input));
        return FunctionToolCallback.builder("NotifyChannel", function)
                .description("""
                        查询当前项目可用的消息 Channel，或在用户明确要求通知时发送消息。
                        action=list 不需要其他参数；action=send 必须提供 channelId、target 和 content。
                        projectId、操作者和 runId 由平台注入，模型不能跨项目发送。发送结果统一进入 ToolResultStore 和审计。
                        """)
                .inputType(ChannelInput.class)
                .build();
    }

    private Map<String, Object> execute(String projectId, String actor, String runId, ChannelInput input) {
        if (!StringUtils.hasText(runId)) {
            throw new SecurityException("CHANNEL_TOOL_REQUIRES_CANONICAL_RUN_ID");
        }
        String action = input == null ? "list" : text(input.getAction(), "list").toLowerCase();
        String toolName = switch (action) {
            case "list", "query" -> "channel_list";
            case "send", "notify" -> "channel_send";
            default -> throw new IllegalArgumentException("不支持的 Channel action：" + action);
        };
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (input != null) {
            put(arguments, "channelId", input.getChannelId());
            put(arguments, "target", input.getTarget());
            put(arguments, "content", input.getContent());
        }
        return toolExecutionService.execute(Map.of(
                "projectId", projectId,
                "userId", actor,
                "runId", runId.trim(),
                "executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name(),
                "toolsetId", "channel.notification",
                "toolName", toolName,
                "arguments", arguments), actor);
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) values.put(key, value);
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    public static class ChannelInput {
        private String action;
        private String channelId;
        private String target;
        private String content;
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
