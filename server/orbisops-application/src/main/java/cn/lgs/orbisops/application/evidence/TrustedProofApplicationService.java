package cn.lgs.orbisops.application.evidence;

import cn.lgs.orbisops.domain.evidence.adapter.repository.ITrustedProofRepository;
import cn.lgs.orbisops.domain.evidence.model.TrustedProof;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofCriteria;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofDraft;
import cn.lgs.orbisops.domain.evidence.service.TrustedProofPolicy;

import java.util.Map;
import java.util.Optional;

public final class TrustedProofApplicationService {

    private final ITrustedProofRepository proofs;
    private final EvidenceIdentityFactory identities;
    private final EvidenceAuditPort audit;
    private final EvidenceTransactionPort transactions;
    private final TrustedProofPolicy policy;
    private final boolean autoInit;

    public TrustedProofApplicationService(
            ITrustedProofRepository proofs,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            boolean autoInit) {
        this(proofs, identities, audit, transactions, autoInit, new TrustedProofPolicy());
    }

    TrustedProofApplicationService(
            ITrustedProofRepository proofs,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            boolean autoInit,
            TrustedProofPolicy policy) {
        if (proofs == null) throw new IllegalArgumentException("TRUSTED_PROOF_REPOSITORY_REQUIRED");
        if (identities == null) throw new IllegalArgumentException("EVIDENCE_IDENTITY_FACTORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("EVIDENCE_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("EVIDENCE_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("TRUSTED_PROOF_POLICY_REQUIRED");
        this.proofs = proofs;
        this.identities = identities;
        this.audit = audit;
        this.transactions = transactions;
        this.policy = policy;
        this.autoInit = autoInit;
    }

    public TrustedProof record(TrustedProofDraft draft) {
        policy.requireRecordable(draft);
        TrustedProof proof = new TrustedProof(
                draft.proofId().isBlank() ? identities.nextProofId() : draft.proofId(),
                draft.projectId(), draft.packageId(), draft.packageVersion(), draft.packageHash(),
                draft.proofType(), draft.source(), draft.externalRunId(), draft.commandHash(),
                draft.scriptHash(), draft.resultStatus(), draft.riskLevel(), draft.metadata(),
                draft.actor(), identities.now());
        return transactions.required(() -> {
            TrustedProof saved = proofs.save(proof);
            audit.record(new EvidenceAuditPort.EvidenceAuditEvent(
                    saved.projectId(), "trusted-proof", "record", saved.proofId(),
                    Map.of(
                            "packageId", saved.packageId(),
                            "packageVersion", saved.packageVersion(),
                            "packageHash", saved.packageHash(),
                            "proofType", saved.proofType(),
                            "source", saved.source().name(),
                            "resultStatus", saved.resultStatus().name())));
            return saved;
        });
    }

    public boolean verify(TrustedProofCriteria criteria) {
        return find(criteria).isPresent();
    }

    public Optional<TrustedProof> find(TrustedProofCriteria criteria) {
        if (criteria == null) throw new IllegalArgumentException("TRUSTED_PROOF_CRITERIA_REQUIRED");
        return proofs.find(criteria).filter(proof -> policy.matches(proof, criteria));
    }

    public EvidenceStoreReadiness readiness() {
        if (proofs.persistent()) {
            return new EvidenceStoreReadiness(
                    "TrustedProofStore", "UP", autoInit,
                    proofs.memoryFallbackAllowed(), "", proofs.count());
        }
        if (proofs.memoryFallbackAllowed()) {
            return new EvidenceStoreReadiness(
                    "TrustedProofStore", "DEGRADED_MEMORY", autoInit,
                    true, "dev/test explicit memory fallback", proofs.count());
        }
        throw new IllegalStateException("TrustedProofService 未配置持久化存储");
    }
}
