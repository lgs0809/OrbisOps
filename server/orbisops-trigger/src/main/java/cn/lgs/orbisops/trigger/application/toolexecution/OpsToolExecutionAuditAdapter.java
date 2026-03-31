package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsToolExecutionAuditAdapter implements ToolExecutionAuditPort {

    private final OpsConfigAuditService audits;

    public OpsToolExecutionAuditAdapter(OpsConfigAuditService audits) {
        this.audits = audits;
    }

    @Override
    public void record(ToolExecutionAuditEvent event) {
        if (event == null) return;
        String projectionId = projectionId(event.payload());
        String deliveryKey = projectionId.isBlank()
                ? ""
                : "tool-completion-audit:" + projectionId;
        audits.recordIdempotent(
                event.projectId(), "tool-execution", event.action(),
                event.targetId(), null, event.payload(), deliveryKey);
    }

    private String projectionId(Object payload) {
        if (!(payload instanceof java.util.Map<?, ?> values)) return "";
        return text(values.get("projectionId"));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
