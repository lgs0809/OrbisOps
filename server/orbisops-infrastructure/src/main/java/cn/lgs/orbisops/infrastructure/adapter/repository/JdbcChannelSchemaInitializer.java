package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dev/test compatibility bootstrap. Production keeps both switches disabled and uses migration manifests.
 */
@Component
public final class JdbcChannelSchemaInitializer {

    private final OpsChannelRepository channelRepository;
    private final OpsChannelOutboxRepository outboxRepository;

    @Value("${ops.channel.auto-init:false}")
    private boolean channelAutoInit;

    @Value("${ops.channel.notification.outbox.auto-init:false}")
    private boolean outboxAutoInit;

    public JdbcChannelSchemaInitializer(OpsChannelRepository channelRepository,
                                        OpsChannelOutboxRepository outboxRepository) {
        if (channelRepository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (outboxRepository == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_REPOSITORY_REQUIRED");
        this.channelRepository = channelRepository;
        this.outboxRepository = outboxRepository;
    }

    @PostConstruct
    public void initialize() {
        if (channelAutoInit) channelRepository.ensureSchema();
        if (outboxAutoInit && outboxRepository.available()) outboxRepository.ensureSchema();
    }
}
