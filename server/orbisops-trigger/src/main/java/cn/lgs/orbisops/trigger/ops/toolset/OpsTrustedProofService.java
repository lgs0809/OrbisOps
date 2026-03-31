package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.evidence.TrustedProofApplicationService;
import cn.lgs.orbisops.trigger.application.evidence.OpsTrustedProofMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OpsTrustedProofService {

    private final TrustedProofApplicationService application;
    private final OpsTrustedProofMapper mapper;

    public OpsTrustedProofService(
            TrustedProofApplicationService application,
            OpsTrustedProofMapper mapper) {
        this.application = application;
        this.mapper = mapper;
    }

    public void init() {
        // Schema initialization is owned by Infrastructure.
    }

    public Map<String, Object> recordTrustedProof(Map<String, Object> request, String actor) {
        return mapper.view(application.record(mapper.draft(request, actor)));
    }

    public boolean verifyTrustedProof(
            String projectId,
            String packageId,
            int packageVersion,
            String packageHash,
            String riskLevel,
            String proofType,
            String externalRunIdOrProofId) {
        return application.verify(mapper.criteria(
                projectId, packageId, packageVersion, packageHash,
                riskLevel, proofType, externalRunIdOrProofId));
    }

    public Map<String, Object> findTrustedProof(
            String projectId,
            String packageId,
            int packageVersion,
            String packageHash,
            String riskLevel,
            String proofType,
            String externalRunIdOrProofId) {
        return application.find(mapper.criteria(
                        projectId, packageId, packageVersion, packageHash,
                        riskLevel, proofType, externalRunIdOrProofId))
                .map(mapper::view)
                .orElseGet(Map::of);
    }

    public Map<String, Object> readiness() {
        return mapper.view(application.readiness());
    }

    public void ensureTables() {
        // Schema initialization is owned by Infrastructure.
    }
}
