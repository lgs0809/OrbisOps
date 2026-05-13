package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface SemanticMemoryWritePort {

    void append(MemoryMessageView message);

    default String appendDurably(MemoryMessageView message) {
        append(message);
        return "WRITTEN";
    }
}
