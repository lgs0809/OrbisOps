package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpsChannelNotificationOutboxJob {

    private final OpsChannelNotificationService notificationService;

    @Value("${orbisops.channel.notification.outbox.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    @Value("${orbisops.channel.notification.outbox.batch-size:20}")
    private int batchSize;

    public OpsChannelNotificationOutboxJob(OpsChannelNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${orbisops.channel.notification.outbox.fixed-delay-ms:60000}")
    public void processPending() {
        if (!schedulerEnabled) return;
        try {
            notificationService.processPending(batchSize);
        } catch (Exception e) {
            log.warn("处理 Channel 通知 outbox 失败：{}", e.getMessage());
        }
    }
}
