package cn.lgs.orbisops.domain.evidence.service;

import cn.lgs.orbisops.domain.evidence.model.TrustedProof;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofCriteria;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofDraft;

public final class TrustedProofPolicy {

    public void requireRecordable(TrustedProofDraft draft) {
        if (draft == null) throw new IllegalArgumentException("TRUSTED_PROOF_DRAFT_REQUIRED");
        if (draft.source() == null) throw new IllegalArgumentException("TRUSTED_PROOF_SOURCE_REQUIRED");
    }

    public boolean matches(TrustedProof proof, TrustedProofCriteria criteria) {
        return proof != null && proof.matches(criteria);
    }
}
