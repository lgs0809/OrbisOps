package cn.lgs.orbisops.trigger.application.evidence;

import cn.lgs.orbisops.application.evidence.EvidenceStoreReadiness;
import cn.lgs.orbisops.domain.evidence.model.EvidenceDraft;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsEvidenceMapper {

    public EvidenceDraft draft(
            String projectId,
            String runId,
            String sourceType,
            String sourceId,
            String toolResultId,
            String outputHash,
            String fullOutputRef,
            String summary,
            boolean verified,
            Map<String, Object> metadata,
            String actor) {
        return new EvidenceDraft(
                projectId, runId, sourceType, sourceId, toolResultId, outputHash,
                fullOutputRef, summary, verified, metadata, actor);
    }

    public Map<String, Object> view(EvidenceRecord evidence) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("evidenceId", evidence.evidenceId());
        data.put("projectId", evidence.projectId());
        data.put("runId", evidence.runId());
        data.put("sourceType", evidence.sourceType());
        data.put("sourceId", evidence.sourceId());
        data.put("toolResultId", evidence.toolResultId());
        data.put("outputHash", evidence.outputHash());
        data.put("fullOutputRef", evidence.fullOutputRef());
        data.put("summary", evidence.summary());
        data.put("verified", evidence.verified());
        data.put("metadata", evidence.metadata());
        data.put("idempotencyKey", evidence.idempotencyKey());
        data.put("createdBy", evidence.createdBy());
        data.put("createdAt", evidence.createdAt());
        return data;
    }

    public List<Map<String, Object>> views(List<EvidenceRecord> evidence) {
        return evidence == null ? List.of() : evidence.stream().map(this::view).toList();
    }

    public Map<String, Object> view(EvidenceStoreReadiness readiness) {
        return Map.of(
                "store", readiness.store(),
                "status", readiness.status(),
                "autoInit", readiness.autoInit(),
                "memoryFallbackAllowed", readiness.memoryFallbackAllowed(),
                "recordCount", readiness.recordCount());
    }
}
