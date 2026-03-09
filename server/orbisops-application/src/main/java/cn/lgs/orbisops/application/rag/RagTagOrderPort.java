package cn.lgs.orbisops.application.rag;

/** Narrow boundary for registering the logical RAG name/tag association. */
public interface RagTagOrderPort {

    void create(String ragName, String knowledgeTag);
}
