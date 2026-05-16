package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Owns typed run persistence, explicit in-memory fallback, and bounded recent-run retention. */
@Slf4j
final class OpsAnalysisRunStore {

    private final IAnalysisRunRepository repository;
    private final OpsAnalysisRunPersistenceMapper persistenceMapper;
    private final OpsAnalysisRunSettings settings;
    private final Map<String, OpsAgentRunRecordDTO> memoryRuns = new ConcurrentHashMap<>();
    private volatile boolean storageUnavailableLogged;

    OpsAnalysisRunStore(
            IAnalysisRunRepository repository,
            OpsAnalysisRunPersistenceMapper persistenceMapper,
            OpsAnalysisRunSettings settings) {
        this.repository = repository;
        this.persistenceMapper = persistenceMapper;
        this.settings = settings == null ? OpsAnalysisRunSettings.defaults() : settings;
    }

    void save(OpsAgentRunRecordDTO run) {
        persist(run);
        memoryRuns.put(run.getRunId(), run);
        trimMemoryRuns();
    }

    Optional<OpsAgentRunRecordDTO> get(String runId) {
        OpsAgentRunRecordDTO persisted = getPersisted(runId);
        if (persisted != null) {
            return Optional.of(persisted);
        }
        return Optional.ofNullable(memoryRuns.get(runId));
    }

    List<OpsAgentRunRecordDTO> list(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<OpsAgentRunRecordDTO> persisted = listPersisted(safeLimit);
        if (!persisted.isEmpty()) {
            return persisted;
        }
        return memoryRuns.values().stream()
                .sorted(Comparator.comparing(
                        OpsAgentRunRecordDTO::getUpdatedAt,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .limit(safeLimit)
                .toList();
    }

    int activeCountByProject(String projectId) {
        IAnalysisRunRepository availableRepository = requiredRepository();
        if (availableRepository == null) {
            String normalizedProjectId = safeText(projectId);
            return (int) memoryRuns.values().stream()
                    .filter(run -> run.getRequest() != null
                            && safeText(run.getRequest().getProjectId()).equals(normalizedProjectId))
                    .filter(run -> OpsAnalysisRunStatus.PENDING.equals(run.getStatus())
                            || OpsAnalysisRunStatus.RUNNING.equals(run.getStatus()))
                    .count();
        }
        try {
            return availableRepository.countActiveByProject(safeText(projectId));
        } catch (Exception e) {
            handleStorageFailure(e);
            return 0;
        }
    }

    private void trimMemoryRuns() {
        int maximum = settings.maxMemoryRecords();
        if (memoryRuns.size() <= maximum) {
            return;
        }
        List<String> toRemove = memoryRuns.values().stream()
                .sorted(Comparator.comparing(
                        OpsAgentRunRecordDTO::getUpdatedAt,
                        Comparator.nullsLast(String::compareTo)))
                .limit(memoryRuns.size() - maximum)
                .map(OpsAgentRunRecordDTO::getRunId)
                .toList();
        toRemove.forEach(memoryRuns::remove);
    }

    private void persist(OpsAgentRunRecordDTO run) {
        IAnalysisRunRepository availableRepository = requiredRepository();
        if (availableRepository == null) {
            return;
        }
        try {
            availableRepository.save(persistenceMapper.snapshot(run));
        } catch (Exception e) {
            handleStorageFailure(e);
        }
    }

    private OpsAgentRunRecordDTO getPersisted(String runId) {
        IAnalysisRunRepository availableRepository = requiredRepository();
        if (availableRepository == null) {
            return null;
        }
        try {
            return availableRepository.find(runId).map(persistenceMapper::record).orElse(null);
        } catch (Exception e) {
            handleStorageFailure(e);
            return null;
        }
    }

    private List<OpsAgentRunRecordDTO> listPersisted(int limit) {
        IAnalysisRunRepository availableRepository = requiredRepository();
        if (availableRepository == null) {
            return List.of();
        }
        try {
            return availableRepository.findRecent(limit).stream()
                    .map(persistenceMapper::record)
                    .toList();
        } catch (Exception e) {
            handleStorageFailure(e);
            return List.of();
        }
    }

    private IAnalysisRunRepository requiredRepository() {
        if (repository != null && repository.available()) {
            return repository;
        }
        if (settings.allowInMemoryFallback()) {
            return null;
        }
        throw new IllegalStateException("ANALYSIS_TASK_STORE_UNAVAILABLE：异步分析任务存储未配置");
    }

    private void handleStorageFailure(Exception error) {
        if (!settings.allowInMemoryFallback()) {
            throw new IllegalStateException(
                    "ANALYSIS_TASK_STORE_UNAVAILABLE：异步分析任务存储读写失败",
                    error);
        }
        if (!storageUnavailableLogged) {
            storageUnavailableLogged = true;
            log.warn("运维分析任务落库不可用，显式测试开关允许降级为内存最近记录：{}", error.getMessage());
        }
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
