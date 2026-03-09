package cn.lgs.orbisops.application.audit;

import cn.lgs.orbisops.domain.audit.adapter.repository.IAnalysisAuditRepository;
import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import cn.lgs.orbisops.domain.audit.service.AnalysisAuditPolicy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class AnalysisAuditApplicationService {

    private final IAnalysisAuditRepository repository;
    private final AnalysisAuditPolicy policy;
    private final int historyCapacity;
    private final Deque<AnalysisAuditRecord> recent = new ArrayDeque<>();

    public AnalysisAuditApplicationService(
            IAnalysisAuditRepository repository,
            int historyCapacity) {
        this(repository, historyCapacity, new AnalysisAuditPolicy());
    }

    AnalysisAuditApplicationService(
            IAnalysisAuditRepository repository,
            int historyCapacity,
            AnalysisAuditPolicy policy) {
        if (repository == null) throw new IllegalArgumentException("ANALYSIS_AUDIT_REPOSITORY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("ANALYSIS_AUDIT_POLICY_REQUIRED");
        this.repository = repository;
        this.policy = policy;
        this.historyCapacity = policy.historyCapacity(historyCapacity);
    }

    public void append(AnalysisAuditRecord record) {
        if (record == null) return;
        synchronized (recent) {
            recent.addFirst(record);
            while (recent.size() > historyCapacity) recent.removeLast();
        }
        repository.upsert(record);
    }

    public List<AnalysisAuditRecord> list(int limit) {
        int safeLimit = policy.listLimit(limit);
        List<AnalysisAuditRecord> persisted = repository.list(safeLimit);
        if (persisted != null && !persisted.isEmpty()) return List.copyOf(persisted);
        synchronized (recent) {
            return recent.stream().limit(safeLimit).toList();
        }
    }
}
