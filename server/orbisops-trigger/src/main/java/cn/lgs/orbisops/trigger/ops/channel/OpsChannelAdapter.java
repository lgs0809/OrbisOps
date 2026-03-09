package cn.lgs.orbisops.trigger.ops.channel;

import java.util.Map;

public interface OpsChannelAdapter {
    String type();

    void validateConfiguration(Map<String, Object> channel);

    Map<String, Object> send(Map<String, Object> channel,
                             String externalConversationId,
                             String content,
                             Map<String, Object> metadata);
}
