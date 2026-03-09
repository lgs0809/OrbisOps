package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface HotMemoryWritePort {

    void append(MemoryMessageView message, int bufferSize);
}
