package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelInboundProcessManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class OpsChannelInboundWorker {

    private final ChannelInboundProcessManager processManager;

    public OpsChannelInboundWorker(ChannelInboundProcessManager processManager) {
        this.processManager = processManager;
    }

    @Scheduled(fixedDelayString = "${orbisops.channel.inbound.worker.fixed-delay-ms:5000}")
    public void processPending() {
        processManager.processPending();
    }
}
