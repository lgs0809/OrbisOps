package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelAuditPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public final class OpsChannelAuditAdapter implements ChannelAuditPort, ChannelRuntimeAuditPort {

    private final OpsConfigAuditService audit;

    public OpsChannelAuditAdapter(OpsConfigAuditService audit) {
        this.audit = audit;
    }

    @Override
    public void record(String projectId,
                       String module,
                       String action,
                       String targetId,
                       Object before,
                       Object after) {
        audit.record(projectId, module, action, targetId, before, after);
    }

    @Override
    public void record(String projectId,
                       String agentId,
                       String actor,
                       String action,
                       String targetId,
                       String riskLevel,
                       String resultStatus,
                       java.util.Map<String, Object> payload) {
        audit.recordRuntimeEvent(projectId, agentId, actor, "channel", action, targetId,
                riskLevel, resultStatus, payload);
    }
}
