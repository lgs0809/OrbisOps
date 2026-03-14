package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelProtocolDescriptor;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

import java.util.List;

public interface ChannelProtocolCatalogPort {

    List<ChannelProtocolDescriptor> supportedTypes();

    void validateConfiguration(ChannelRecord channel);
}
