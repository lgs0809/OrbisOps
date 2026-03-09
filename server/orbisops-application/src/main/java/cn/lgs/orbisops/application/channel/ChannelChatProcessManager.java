package cn.lgs.orbisops.application.channel;

import java.util.Map;

public final class ChannelChatProcessManager {

    private final ChannelOutboundApplicationService outbound;

    public ChannelChatProcessManager(ChannelOutboundApplicationService outbound) {
        if (outbound == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_SERVICE_REQUIRED");
        this.outbound = outbound;
    }

    public Map<String, Object> send(ChannelModels.Send command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_SEND_COMMAND_REQUIRED");
        return outbound.send(command);
    }
}
