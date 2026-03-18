package cn.lgs.orbisops.application.evidence;

import cn.lgs.orbisops.domain.evidence.adapter.repository.IEvidenceRepository;
import cn.lgs.orbisops.domain.evidence.model.EvidenceDraft;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import cn.lgs.orbisops.domain.evidence.service.EvidencePolicy;

import java.util.List;
import java.util.Map;

public final class EvidenceApplicationService {

    private final IEvidenceRepository evidence;
    private final EvidenceIdentityFactory identities;
    private final EvidenceAuditPort audit;
    private final EvidenceTransactionPort transactions;
    private final EvidencePolicy policy;
    private final boolean autoInit;

    public EvidenceApplicationService(
            IEvidenceRepository evidence,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            boolean autoInit) {
        this(evidence, identities, audit, transactions, autoInit, new EvidencePolicy());
    }

    EvidenceApplicationService(
            IEvidenceRepository evidence,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            boolean autoInit,
            EvidencePolicy policy) {
        if (evidence == null) throw new IllegalArgumentException("EVIDENCE_REPOSITORY_REQUIRED");
        if (identities == null) throw new IllegalArgumentException("EVIDENCE_IDENTITY_FACTORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("EVIDENCE_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("EVIDENCE_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("EVIDENCE_POLICY_REQUIRED");
        this.evidence = evidence;
        this.identities = identities;
        this.audit = audit;
        this.transactions = transactions;
        this.policy = policy;
        this.autoInit = autoInit;
    }

    public EvidenceRecord record(EvidenceDraft draft) {
        if (draft == null) throw new IllegalArgumentException("EVIDENCE_DRAFT_REQUIRED");
        String key = policy.idempotencyKey(draft);
        EvidenceRecord existing = evidence.findByIdempotencyKey(key).orElse(null);
        EvidenceRecord candidate = new EvidenceRecord(
                existing == null ? identities.nextEvidenceId() : existing.evidenceId(),
                draft.projectId(), draft.runId(), draft.sourceType(), draft.sourceId(),
                draft.toolResultId(), draft.outputHash(), draft.fullOutputRef(), draft.summary(),
                draft.verified(), draft.metadata(), key, draft.actor(),
                existing == null ? identities.now() : existing.createdAt());
        return transactions.required(() -> {
            EvidenceRecord saved = evidence.save(candidate);
            audit.record(new EvidenceAuditPort.EvidenceAuditEvent(
                    saved.projectId(), "evidence", "record", saved.evidenceId(),
                    Map.of(
                            "runId", saved.runId(),
                            "toolResultId", saved.toolResultId(),
                            "outputHash", saved.outputHash(),
                            "verified", saved.verified())));
            return saved;
        });
    }

    public EvidenceRecord require(String evidenceId, String projectId, String runId) {
        String id = required(evidenceId, "EVIDENCE_ID_REQUIRED");
        EvidenceRecord record = evidence.findScoped(id, required(projectId, "EVIDENCE_PROJECT_ID_REQUIRED"),
                        required(runId, "EVIDENCE_RUN_ID_REQUIRED"))
                .orElseThrow(() -> new SecurityException(
                        "EVIDENCE_NOT_FOUND_OR_CROSS_SCOPE：Evidence 不属于当前 project/run"));
        record.requireScope(projectId, runId);
        record.requireComplete();
        return record;
    }

    public List<EvidenceRecord> listForRun(String projectId, String runId, int limit) {
        return evidence.listForRun(
                required(projectId, "EVIDENCE_PROJECT_ID_REQUIRED"),
                required(runId, "EVIDENCE_RUN_ID_REQUIRED"),
                policy.listLimit(limit));
    }

    public EvidenceStoreReadiness readiness() {
        return new EvidenceStoreReadiness(
                "EvidenceStore", "UP", autoInit, false, "", evidence.count());
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
