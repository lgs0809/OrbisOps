package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

import java.util.List;
import java.util.Map;

public interface ChannelProtocolCatalogPort {

    List<Map<String, Object>> supportedTypes();

    void validateConfiguration(ChannelRecord channel);
}
