package cn.lgs.orbisops.application.rag;

/** Audit boundary for legacy RAG order configuration changes. */
public interface RagOrderAuditPort {

    void created(RagOrderDefinition definition);

    void updated(
            String action,
            String targetId,
            RagOrderDefinition before,
            RagOrderDefinition after);

    void deleted(
            String action,
            String targetId,
            RagOrderDefinition before,
            boolean deleted);
}
