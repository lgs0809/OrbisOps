package cn.lgs.orbisops.domain.alert.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxEntry;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;

import java.util.List;
import java.util.Optional;

public interface IAlertOutboxRepository {

    long countActiveByProject(String projectId);

    boolean preemptOneLowerPriority(String projectId, int priority);

    void upsert(AlertOutboxDraft draft);

    Optional<AlertOutboxEntry> findByDispatchKey(String dispatchKey);

    List<AlertOutboxEntry> listDispatchable(int maxAttempts, int limit);

    boolean claim(long id, String claimId, int maxAttempts);

    boolean markSucceeded(long id, String claimId, String runId);

    boolean markFailed(long id, String claimId, AlertOutboxFailurePlan failure);

    int recoverStale(int lockTimeoutSeconds, int maxAttempts);

    int deadLetterExhausted(int maxAttempts);
}
