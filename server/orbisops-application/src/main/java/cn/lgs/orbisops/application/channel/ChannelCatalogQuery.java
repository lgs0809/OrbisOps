package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelProtocolDescriptor;

import java.util.List;
import java.util.Map;

public interface ChannelCatalogQuery {

    List<Map<String, Object>> list(String projectId);

    List<ChannelProtocolDescriptor> supportedTypes();

    List<Map<String, Object>> messages(String projectId, String channelId, int limit);

    Map<String, Object> status(String projectId);

    Map<String, Object> statusAll();

    List<Map<String, Object>> identities(String projectId, String channelId);

    Map<String, Object> get(String projectId, String channelId);
}
