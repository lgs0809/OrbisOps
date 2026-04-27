package cn.lgs.orbisops.domain.channel.adapter.repository;

import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IChannelOutboxRepository {

    boolean available();

    long enqueue(ChannelOutboxRecord record);

    void recoverExpiredLeases(int maxAttempts);

    List<Long> findDispatchableIds(int maxAttempts, int limit);

    boolean tryAcquireLease(long id, String lockToken, LocalDateTime leaseExpiresAt, int maxAttempts);

    Optional<ChannelOutboxRecord> findById(long id);

    Optional<ChannelOutboxRecord> findByProjectAndId(String projectId, long id);

    List<ChannelOutboxRecord> findByProject(String projectId, int limit);

    void markSucceeded(long id, String lockToken, String response);

    void markFailed(long id, String lockToken, int retryCount, LocalDateTime nextRetryAt,
                    boolean deadLetter, String error);

    boolean requeue(String projectId, long id);

    boolean cancel(String projectId, long id);

    ChannelOutboxStatus status(String projectId);

    ChannelOutboxStatus statusAll();
}
