package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface SemanticMemoryWritePort {

    void append(MemoryMessageView message);

    default String appendDurably(MemoryMessageView message) {
        append(message);
        return "WRITTEN";
    }
    default Outcome appendDurably(cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob job) {
        var source=job.message();
        return new Outcome(appendDurably(new MemoryMessageView(source.sessionId(),source.userId(),source.role(),source.content(),source.createdAt(),source.metadata())),false,"");
    }
    record Outcome(String storage,boolean modelPending,String reason) {}

}
