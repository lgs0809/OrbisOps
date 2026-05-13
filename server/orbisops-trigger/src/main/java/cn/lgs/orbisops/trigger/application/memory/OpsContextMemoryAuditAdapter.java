package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryAuditEvent;
import cn.lgs.orbisops.application.memory.ContextMemoryAuditPort;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Trigger adapter from typed Context Memory mutation events to the historical runtime audit service. */
@Component
public class OpsContextMemoryAuditAdapter implements ContextMemoryAuditPort {

    private final ObjectProvider<OpsConfigAuditService> auditServiceProvider;
    private final OpsContextMemoryMapper mapper = new OpsContextMemoryMapper();

    public OpsContextMemoryAuditAdapter(ObjectProvider<OpsConfigAuditService> auditServiceProvider) {
        this.auditServiceProvider = auditServiceProvider;
    }

    @Override
    public void record(ContextMemoryAuditEvent event) {
        if (event == null || event.snapshot() == null) return;
        OpsConfigAuditService auditService = auditServiceProvider == null
                ? null
                : auditServiceProvider.getIfAvailable();
        if (auditService == null) return;
        ContextMemorySnapshot snapshot = event.snapshot();
        boolean statusUpdate = "update-status".equals(event.action());
        Map<String, Object> payload = statusUpdate
                ? mapper.view(snapshot)
                : mutationPayload(snapshot);
        auditService.recordRuntimeEvent(
                "PROJECT".equals(snapshot.scopeType()) ? snapshot.scopeId() : "",
                "",
                statusUpdate ? snapshot.createdBy() : "",
                "context-memory",
                event.action(),
                snapshot.memoryId(),
                "LOW",
                "SUCCEEDED",
                payload);
    }

    private Map<String, Object> mutationPayload(ContextMemorySnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memoryId", snapshot.memoryId());
        payload.put("scopeType", snapshot.scopeType());
        payload.put("scopeId", snapshot.scopeId());
        payload.put("memoryType", snapshot.memoryType());
        payload.put("title", snapshot.title());
        payload.put("status", snapshot.status());
        payload.put("sourceType", snapshot.sourceType());
        payload.put("sourceId", snapshot.sourceId());
        return payload;
    }
}
