package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class ChannelToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "channel";
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(definitions.toolset(
                "channel.notification",
                "Channel 消息",
                "列出项目 Channel，或向已配置目标发送通知",
                "CHANNEL",
                false,
                List.of(
                        tools.read("channel_list", "列出当前项目可用 Channel", "CHANNEL"),
                        tools.channel("channel_send", "通过项目已配置 Channel 发送通知"))));
    }
}
