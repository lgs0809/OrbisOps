package cn.lgs.orbisops.application.channel;

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
}
