package cn.lgs.orbisops.domain.evidence.adapter.repository;

import cn.lgs.orbisops.domain.evidence.model.TrustedProof;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofCriteria;

import java.util.Optional;

public interface ITrustedProofRepository {

    TrustedProof save(TrustedProof proof);

    Optional<TrustedProof> find(TrustedProofCriteria criteria);

    long count();

    boolean persistent();

    boolean memoryFallbackAllowed();
}
