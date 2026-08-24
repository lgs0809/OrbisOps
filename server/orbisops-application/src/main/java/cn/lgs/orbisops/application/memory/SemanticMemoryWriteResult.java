package cn.lgs.orbisops.application.memory;

/** Fallback storage and deferred model intent are separate facts. */
public record SemanticMemoryWriteResult(boolean written,String storage,boolean modelPending,String reason) {
    public SemanticMemoryWriteResult {storage=storage==null?"":storage;reason=reason==null?"":reason;}
    public SemanticMemoryWriteResult(boolean written,String storage) {this(written,storage,false,"");}
    public static SemanticMemoryWriteResult vector() {return new SemanticMemoryWriteResult(true,"vector",false,"VECTOR_WRITTEN");}
    public static SemanticMemoryWriteResult lexical() {return new SemanticMemoryWriteResult(true,"lexical",false,"INTENTIONAL_LEXICAL");}
    public static SemanticMemoryWriteResult lexicalPending(String reason) {return new SemanticMemoryWriteResult(true,"lexical",true,reason);}
    public static SemanticMemoryWriteResult skipped() {return new SemanticMemoryWriteResult(false,"",false,"SKIPPED");}
}
