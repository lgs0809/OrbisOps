package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelTaskExecutorPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public final class OpsChannelTaskExecutorAdapter implements ChannelTaskExecutorPort {

    private final Executor executor;

    public OpsChannelTaskExecutorAdapter(@Qualifier("opsSubAgentExecutor") Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }
}
