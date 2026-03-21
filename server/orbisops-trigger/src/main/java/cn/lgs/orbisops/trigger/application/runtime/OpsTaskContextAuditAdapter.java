package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextAuditPort;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsTaskContextAuditAdapter implements TaskContextAuditPort {

    private final ObjectProvider<OpsConfigAuditService> auditServiceProvider;
    private final OpsTaskContextMapper mapper;

    public OpsTaskContextAuditAdapter(
            ObjectProvider<OpsConfigAuditService> auditServiceProvider,
            OpsTaskContextMapper mapper) {
        if (auditServiceProvider == null) {
            throw new IllegalArgumentException("TASK_CONTEXT_AUDIT_PROVIDER_REQUIRED");
        }
        if (mapper == null) throw new IllegalArgumentException("TASK_CONTEXT_MAPPER_REQUIRED");
        this.auditServiceProvider = auditServiceProvider;
        this.mapper = mapper;
    }

    @Override
    public void record(TaskContextSnapshot before, TaskContextSnapshot after) {
        if (after == null) return;
        OpsConfigAuditService audit = auditServiceProvider.getIfAvailable();
        if (audit == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", after.runId());
        payload.put("taskState", after.state().name());
        payload.put("summary", abbreviate(after.summary(), 300));
        payload.put("context", mapper.auditContext(after.content()));
        payload.put("version", after.version());
        audit.recordRuntimeEvent(
                after.projectId(),
                after.agentId(),
                "",
                "task-context",
                before == null ? "create" : "update",
                after.runId(),
                "LOW",
                after.state().name(),
                payload);
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength)) + "...";
    }
}
