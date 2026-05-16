package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpClientRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduling adapter for MCP client cache cleanup. */
@Component
public class OpsMcpClientCacheCleanupJob {

    private final OpsMcpClientRegistry clientRegistry;

    public OpsMcpClientCacheCleanupJob(OpsMcpClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @Scheduled(fixedDelayString = "#{@opsMcpClientCacheSettings.cleanupIntervalMillis()}")
    public void cleanupExpiredClients() {
        clientRegistry.cleanupExpiredClients();
    }
}
