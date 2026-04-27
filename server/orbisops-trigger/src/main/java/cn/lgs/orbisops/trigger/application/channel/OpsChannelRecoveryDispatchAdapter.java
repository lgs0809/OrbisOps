package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRecoveryDispatchPort;
import cn.lgs.orbisops.application.channel.ChannelInboundProcessManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public final class OpsChannelRecoveryDispatchAdapter implements ChannelRecoveryDispatchPort {

    private final ChannelInboundProcessManager processManager;
    private final Executor executor;

    public OpsChannelRecoveryDispatchAdapter(ChannelInboundProcessManager processManager,
                                             @Qualifier("opsSubAgentExecutor") Executor executor) {
        this.processManager = processManager;
        this.executor = executor;
    }

    @Override
    public void dispatch(String channelId, String externalMessageId) {
        executor.execute(() -> processManager.processQueued(channelId, externalMessageId));
    }
}
