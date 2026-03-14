package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;

import java.util.Map;

public final class ReceiveChannelMessageUseCase {

    private final ChannelInboundProcessManager processManager;

    public ReceiveChannelMessageUseCase(ChannelInboundProcessManager processManager) {
        if (processManager == null) throw new IllegalArgumentException("CHANNEL_INBOUND_PROCESS_MANAGER_REQUIRED");
        this.processManager = processManager;
    }

    public Map<String, Object> receive(ChannelModels.Receive command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_RECEIVE_COMMAND_REQUIRED");
        return processManager.receive(command);
    }

    public Map<String, Object> receiveProvider(String channelId, ChannelInboundEnvelope envelope) {
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("CHANNEL_ID_REQUIRED");
        if (envelope == null) throw new IllegalArgumentException("CHANNEL_PROVIDER_ENVELOPE_REQUIRED");
        return processManager.receiveProvider(channelId, envelope);
    }
}
