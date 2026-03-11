package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagOrderAuditPort;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Audit adapter for knowledge-base catalog mutations. */
public final class OpsRagOrderCatalogAdapter implements RagOrderAuditPort {

    private static final String AUDIT_MODULE = "rag-order";

    private final OpsConfigAuditService auditService;

    public OpsRagOrderCatalogAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("RAG_ORDER_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void created(RagOrderDefinition definition) {
        auditService.record(
                AUDIT_MODULE,
                "create",
                definition == null ? null : definition.ragId(),
                null,
                definition);
    }

    @Override
    public void updated(
            String action,
            String targetId,
            RagOrderDefinition before,
            RagOrderDefinition after) {
        auditService.record(
                AUDIT_MODULE,
                action,
                targetId,
                before,
                after);
    }

    @Override
    public void deleted(
            String action,
            String targetId,
            RagOrderDefinition before,
            boolean deleted) {
        auditService.record(
                AUDIT_MODULE,
                action,
                targetId,
                before,
                Map.of("deleted", deleted));
    }
}
