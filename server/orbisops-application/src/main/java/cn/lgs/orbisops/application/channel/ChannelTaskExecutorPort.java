package cn.lgs.orbisops.application.channel;

public interface ChannelTaskExecutorPort {

    void execute(Runnable task);
}
