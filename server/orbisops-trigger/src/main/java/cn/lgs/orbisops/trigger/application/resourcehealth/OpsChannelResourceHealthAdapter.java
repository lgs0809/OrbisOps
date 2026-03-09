package cn.lgs.orbisops.trigger.application.resourcehealth;

import cn.lgs.orbisops.application.resourcehealth.ChannelResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger adapter exposing Channel runtime readiness through an application port. */
@Component
public class OpsChannelResourceHealthAdapter
        implements ChannelResourceHealthProbePort {

    private final OpsChannelNotificationService notificationService;

    public OpsChannelResourceHealthAdapter(
            OpsChannelNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public ResourceHealthCheck probe() {
        Map<String, Object> status = notificationService.statusAll();
        Map<String, Object> detail = status == null ? Map.of() : status;
        boolean healthy = Boolean.TRUE.equals(detail.get("ready"))
                && Boolean.TRUE.equals(detail.get("outboxReady"));
        return new ResourceHealthCheck(
                "channel",
                "消息 Channel",
                "ai_ops_channel",
                healthy,
                healthy
                        ? "Channel 入站、出站存储已就绪"
                        : "尚未配置可用 Channel 或通知存储不可用",
                Map.of("detail", detail));
    }
}
