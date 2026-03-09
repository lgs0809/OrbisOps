package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class OpsChannelExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final ChannelQueryService channels;
    private final ChannelChatProcessManager chat;

    public OpsChannelExecutionDispatchHandler(ChannelQueryService channels, ChannelChatProcessManager chat) {
        this.channels = channels;
        this.chat = chat;
    }

    @Override
    public String handlerId() {
        return "channel";
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "channel.notification".equals(target.toolsetId())
                || "CHANNEL".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        if (channels == null || chat == null) {
            throw new IllegalStateException("Channel application boundary 未初始化");
        }
        Map<String, Object> arguments = request.arguments();
        String projectId = required(first(request.requestContext().get("projectId"), arguments.get("projectId")),
                "Channel 工具必须绑定 projectId");
        return switch (target.toolName()) {
            case "channel_list" -> Map.of("items", channels.list(projectId));
            case "channel_send" -> chat.send(new ChannelModels.Send(
                    projectId,
                    required(arguments.get("channelId"), "发送 Channel 通知必须选择 channelId"),
                    required(arguments.get("target"), "发送 Channel 通知必须填写目标会话或接收人"),
                    required(arguments.get("content"), "发送 Channel 通知必须填写内容"),
                    Map.of(
                            "runId", text(request.requestContext().get("runId")),
                            "sessionId", text(request.requestContext().get("sessionId")),
                            "source", "AGENT_TOOL"),
                    request.actor()));
            default -> throw new IllegalArgumentException("未知 Channel 工具：" + target.toolName());
        };
    }

    private Object first(Object first, Object second) {
        return first != null ? first : second;
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (!StringUtils.hasText(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
