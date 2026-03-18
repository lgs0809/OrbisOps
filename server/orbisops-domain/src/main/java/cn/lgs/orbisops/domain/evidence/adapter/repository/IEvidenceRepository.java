package cn.lgs.orbisops.domain.evidence.adapter.repository;

import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;

import java.util.List;
import java.util.Optional;

public interface IEvidenceRepository {

    EvidenceRecord save(EvidenceRecord evidence);

    Optional<EvidenceRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<EvidenceRecord> findScoped(String evidenceId, String projectId, String runId);

    List<EvidenceRecord> listForRun(String projectId, String runId, int limit);

    long count();
}
