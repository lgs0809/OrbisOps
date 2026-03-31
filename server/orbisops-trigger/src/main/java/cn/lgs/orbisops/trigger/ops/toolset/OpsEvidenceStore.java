package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.trigger.application.evidence.OpsEvidenceMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpsEvidenceStore {

    private final EvidenceApplicationService application;
    private final OpsEvidenceMapper mapper;

    public OpsEvidenceStore(
            EvidenceApplicationService application,
            OpsEvidenceMapper mapper) {
        this.application = application;
        this.mapper = mapper;
    }

    public void init() {
        // Schema initialization is owned by Infrastructure.
    }

    public Map<String, Object> record(
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
        return mapper.view(application.record(mapper.draft(
                projectId, runId, sourceType, sourceId, toolResultId, outputHash,
                fullOutputRef, summary, verified, metadata, actor)));
    }

    public Map<String, Object> require(String evidenceId, String projectId, String runId) {
        return mapper.view(application.require(evidenceId, projectId, runId));
    }

    public List<Map<String, Object>> listForRun(String projectId, String runId, int limit) {
        return mapper.views(application.listForRun(projectId, runId, limit));
    }

    public Map<String, Object> readiness() {
        return mapper.view(application.readiness());
    }

    public void ensureTables() {
        // Schema initialization is owned by Infrastructure.
    }
}
