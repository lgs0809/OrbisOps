package cn.lgs.orbisops.application.channel;

import java.util.Map;

public interface ChannelRuntimeAuditPort {

    void record(String projectId,
                String agentId,
                String actor,
                String action,
                String targetId,
                String riskLevel,
                String resultStatus,
                Map<String, Object> payload);
}
