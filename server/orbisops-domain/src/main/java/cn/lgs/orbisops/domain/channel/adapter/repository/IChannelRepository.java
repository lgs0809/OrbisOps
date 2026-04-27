package cn.lgs.orbisops.domain.channel.adapter.repository;

import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStoreStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IChannelRepository {

    void create(ChannelRecord channel);

    boolean update(ChannelRecord channel);

    Optional<ChannelRecord> findById(String channelId);

    List<ChannelRecord> findAll();

    List<ChannelRecord> findByProject(String projectId);

    boolean insertInbound(ChannelMessageRecord message);

    Optional<ChannelMessageRecord> findInbound(String channelId, String externalMessageId);

    List<ChannelMessageRecord> findQueuedInbound(int limit);

    boolean tryAcquireConversationLease(String channelId,
                                        String externalConversationId,
                                        String senderId,
                                        String lockToken,
                                        Instant leaseExpiresAt);

    void releaseConversationLease(String channelId,
                                  String externalConversationId,
                                  String senderId,
                                  String lockToken);

    boolean isOldestUnfinishedInbound(String channelId,
                                      String externalConversationId,
                                      String senderId,
                                      String externalMessageId);

    boolean tryAcquireInboundLease(String channelId,
                                   String externalMessageId,
                                   String lockToken,
                                   Instant leaseExpiresAt);

    boolean renewInboundLeases(String channelId,
                               String externalConversationId,
                               String senderId,
                               String externalMessageId,
                               String lockToken,
                               Instant leaseExpiresAt);

    boolean markClaimedInboundCompleted(String channelId,
                                        String externalMessageId,
                                        String lockToken,
                                        String status,
                                        String runId,
                                        String sessionId);

    boolean markClaimedInboundFailed(String channelId,
                                     String externalMessageId,
                                     String lockToken,
                                     String errorMessage);

    List<ChannelMessageRecord> quarantineExpiredInboundLeases(int limit);

    boolean requeueRecoveryRequired(String projectId, String channelId, String externalMessageId);

    boolean cancelRecoveryRequired(String projectId, String channelId, String externalMessageId);

    void markInboundQueued(String channelId, String externalMessageId, String runId, String sessionId);

    void markInboundCompleted(String channelId, String externalMessageId, String status, String runId, String sessionId);

    void markInboundFailed(String channelId, String externalMessageId, String errorMessage);

    void insertOutbound(ChannelMessageRecord message);

    void markOutboundCompleted(String messageId, String status, String payloadJson);

    void markOutboundFailed(String messageId, String errorMessage);

    void bindOutboundExternalMessageId(String messageId, String externalMessageId);

    Optional<ChannelMessageRecord> findLatestOutboundByRun(String projectId,
                                                           String channelId,
                                                           String runId,
                                                           String senderId);

    List<ChannelMessageRecord> findMessages(String projectId, String channelId, int limit);

    String resolveSession(String channelId,
                          String projectId,
                          String executionKey,
                          String externalConversationId,
                          String senderId,
                          String candidateSessionId);

    ChannelStoreStatus status(String projectId);

    ChannelStoreStatus statusAll();

    Optional<ChannelIdentityRecord> findIdentity(String channelId, String externalSenderId);

    List<ChannelIdentityRecord> findIdentities(String projectId, String channelId);

    void createIdentity(ChannelIdentityRecord identity);

    boolean updateIdentity(ChannelIdentityRecord identity, long expectedVersion);
}
