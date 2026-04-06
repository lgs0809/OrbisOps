package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelConversationLeasePort;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OpsChannelConversationLeaseAdapter implements ChannelConversationLeasePort {

    private final IChannelRepository repository;

    public OpsChannelConversationLeaseAdapter(IChannelRepository repository) {
        if (repository == null) throw new IllegalArgumentException("OPS_CHANNEL_REPOSITORY_REQUIRED");
        this.repository = repository;
    }

    @Override
    public boolean tryAcquireConversation(String channelId,
                                          String externalConversationId,
                                          String senderId,
                                          String lockToken,
                                          Instant leaseExpiresAt) {
        return repository.tryAcquireConversationLease(channelId, externalConversationId, senderId,
                lockToken, leaseExpiresAt);
    }

    @Override
    public boolean isOldestUnfinishedInbound(String channelId,
                                              String externalConversationId,
                                              String senderId,
                                              String externalMessageId) {
        return repository.isOldestUnfinishedInbound(channelId, externalConversationId, senderId, externalMessageId);
    }

    @Override
    public boolean tryAcquireInbound(String channelId,
                                     String externalMessageId,
                                     String lockToken,
                                     Instant leaseExpiresAt) {
        return repository.tryAcquireInboundLease(channelId, externalMessageId, lockToken, leaseExpiresAt);
    }

    @Override
    public boolean renew(String channelId,
                         String externalConversationId,
                         String senderId,
                         String externalMessageId,
                         String lockToken,
                         Instant leaseExpiresAt) {
        return repository.renewInboundLeases(channelId, externalConversationId, senderId,
                externalMessageId, lockToken, leaseExpiresAt);
    }

    @Override
    public void releaseConversation(String channelId,
                                    String externalConversationId,
                                    String senderId,
                                    String lockToken) {
        repository.releaseConversationLease(channelId, externalConversationId, senderId, lockToken);
    }

    @Override
    public boolean complete(String channelId,
                            String externalMessageId,
                            String lockToken,
                            String status,
                            String runId,
                            String sessionId) {
        return repository.markClaimedInboundCompleted(channelId, externalMessageId, lockToken, status, runId, sessionId);
    }

    @Override
    public boolean fail(String channelId,
                        String externalMessageId,
                        String lockToken,
                        String errorMessage) {
        return repository.markClaimedInboundFailed(channelId, externalMessageId, lockToken, errorMessage);
    }

    @Override
    public List<RecoveredLease> recoverExpired(int limit) {
        return repository.quarantineExpiredInboundLeases(limit).stream().map(this::recoveredLease).toList();
    }

    private RecoveredLease recoveredLease(ChannelMessageRecord record) {
        return new RecoveredLease(record.projectId(), record.channelId(), record.externalConversationId(),
                record.senderId(), record.externalMessageId(), record.runId(), record.sessionId());
    }
}
