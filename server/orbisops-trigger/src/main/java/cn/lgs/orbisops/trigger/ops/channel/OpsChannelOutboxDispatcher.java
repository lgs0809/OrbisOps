package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Durable single-record Channel outbox delivery processor. */
@Slf4j
final class OpsChannelOutboxDispatcher {

    private final IChannelOutboxRepository outboxRepository;
    private final ChannelChatProcessManager channelChatProcessManager;
    private final OpsChannelOutboxDeliveryCommandFactory commandFactory;
    private final OpsChannelOutboxDeliveryFailurePolicy failurePolicy;
    private final Supplier<String> leaseKeySupplier;
    private final Supplier<LocalDateTime> nowSupplier;

    OpsChannelOutboxDispatcher(
            IChannelOutboxRepository outboxRepository,
            ChannelChatProcessManager channelChatProcessManager,
            ChannelOutboundContentPolicy outboundContentPolicy) {
        this(
                outboxRepository,
                channelChatProcessManager,
                outboundContentPolicy,
                () -> UUID.randomUUID().toString(),
                LocalDateTime::now);
    }

    OpsChannelOutboxDispatcher(
            IChannelOutboxRepository outboxRepository,
            ChannelChatProcessManager channelChatProcessManager,
            ChannelOutboundContentPolicy outboundContentPolicy,
            Supplier<String> leaseKeySupplier,
            Supplier<LocalDateTime> nowSupplier) {
        this.outboxRepository = outboxRepository;
        this.channelChatProcessManager = channelChatProcessManager;
        OpsChannelOutboxMetadataCodec metadataCodec = new OpsChannelOutboxMetadataCodec();
        this.commandFactory = new OpsChannelOutboxDeliveryCommandFactory(metadataCodec);
        this.failurePolicy = new OpsChannelOutboxDeliveryFailurePolicy(outboundContentPolicy);
        this.leaseKeySupplier = leaseKeySupplier;
        this.nowSupplier = nowSupplier;
    }

    Result dispatch(Long id, Settings settings) {
        if (id == null) {
            return new Result(false, "Channel 通知任务不存在。", false);
        }
        int maxAttempts = safeMaxAttempts(settings.maxAttempts());
        String leaseKey = leaseKeySupplier.get();
        LocalDateTime leaseExpiresAt = nowSupplier.get()
                .plusSeconds(Math.max(15, settings.leaseSeconds()));
        if (!outboxRepository.tryAcquireLease(
                id,
                leaseKey,
                leaseExpiresAt,
                maxAttempts)) {
            ChannelOutboxRecord row = outboxRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "CHANNEL_OUTBOX_NOT_FOUND"));
            boolean succeeded = "SUCCEEDED".equals(row.status());
            return new Result(
                    succeeded,
                    succeeded
                            ? "Channel 通知已成功处理。"
                            : "Channel 通知正在处理或尚未到重试时间。",
                    false);
        }
        ChannelOutboxRecord task = outboxRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "CHANNEL_OUTBOX_NOT_FOUND"));
        try {
            Map<String, Object> delivery = channelChatProcessManager.send(
                    commandFactory.create(task));
            if (!Boolean.TRUE.equals(delivery.get("delivered"))) {
                throw new IllegalStateException(
                        "CHANNEL_OUTBOUND_NOT_CONFIGURED");
            }
            outboxRepository.markSucceeded(
                    id,
                    leaseKey,
                    abbreviate(String.valueOf(delivery), 2000));
            return new Result(
                    true,
                    "Channel 通知已投递：" + task.channelId(),
                    true);
        } catch (Exception e) {
            OpsChannelOutboxDeliveryFailurePolicy.Assessment assessment = failurePolicy.assess(
                    e,
                    task.retryCount() + 1,
                    maxAttempts);
            outboxRepository.markFailed(
                    id,
                    leaseKey,
                    assessment.retryCount(),
                    nowSupplier.get().plusSeconds(assessment.delaySeconds()),
                    assessment.deadLetter(),
                    assessment.safeError());
            log.warn(
                    "Channel 通知投递失败，outboxId={} retry={} deadLetter={} error={}",
                    id,
                    assessment.retryCount(),
                    assessment.deadLetter(),
                    assessment.safeError());
            return new Result(false, assessment.message(), false);
        }
    }

    private int safeMaxAttempts(int maxAttempts) {
        return Math.max(1, Math.min(maxAttempts, 20));
    }

    private String abbreviate(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max
                ? text
                : text.substring(0, max) + "...";
    }

    record Settings(int maxAttempts, int leaseSeconds) {
    }

    record Result(
            boolean success,
            String message,
            boolean appendExecutionNote) {
    }
}
