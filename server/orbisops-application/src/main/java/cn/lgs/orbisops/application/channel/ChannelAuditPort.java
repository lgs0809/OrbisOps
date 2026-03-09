package cn.lgs.orbisops.application.channel;

public interface ChannelAuditPort {

    void record(String projectId,
                String module,
                String action,
                String targetId,
                Object before,
                Object after);
}
