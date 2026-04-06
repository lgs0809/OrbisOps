package cn.lgs.orbisops.application.channel;

import java.time.Instant;
import java.util.List;

public interface ChannelConversationLeasePort {

    boolean tryAcquireConversation(String channelId,
                                   String externalConversationId,
                                   String senderId,
                                   String lockToken,
                                   Instant leaseExpiresAt);

    boolean isOldestUnfinishedInbound(String channelId,
                                      String externalConversationId,
                                      String senderId,
                                      String externalMessageId);

    boolean tryAcquireInbound(String channelId,
                              String externalMessageId,
                              String lockToken,
                              Instant leaseExpiresAt);

    boolean renew(String channelId,
                  String externalConversationId,
                  String senderId,
                  String externalMessageId,
                  String lockToken,
                  Instant leaseExpiresAt);

    void releaseConversation(String channelId,
                             String externalConversationId,
                             String senderId,
                             String lockToken);

    boolean complete(String channelId,
                     String externalMessageId,
                     String lockToken,
                     String status,
                     String runId,
                     String sessionId);

    boolean fail(String channelId,
                 String externalMessageId,
                 String lockToken,
                 String errorMessage);

    List<RecoveredLease> recoverExpired(int limit);

    record RecoveredLease(String projectId,
                          String channelId,
                          String externalConversationId,
                          String senderId,
                          String externalMessageId,
                          String runId,
                          String sessionId) {
    }
}
