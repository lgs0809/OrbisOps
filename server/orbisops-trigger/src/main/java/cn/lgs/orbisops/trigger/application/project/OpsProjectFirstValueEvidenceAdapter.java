package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.project.ProjectFirstValueEvidencePort;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Proves First Value only from a successful ReAct run with evidence-bound structured facts. */
public final class OpsProjectFirstValueEvidenceAdapter implements ProjectFirstValueEvidencePort {

    private static final int RECENT_LIMIT = 500;

    private final IAnalysisRunRepository runs;
    private final OpsAnalysisRunPersistenceMapper persistenceMapper;
    private final OpsStructuredReportService reports;

    public OpsProjectFirstValueEvidenceAdapter(
            IAnalysisRunRepository runs,
            OpsAnalysisRunPersistenceMapper persistenceMapper,
            OpsStructuredReportService reports) {
        if (runs == null) throw new IllegalArgumentException("ANALYSIS_RUN_REPOSITORY_REQUIRED");
        if (persistenceMapper == null) throw new IllegalArgumentException("ANALYSIS_RUN_MAPPER_REQUIRED");
        if (reports == null) throw new IllegalArgumentException("STRUCTURED_REPORT_SERVICE_REQUIRED");
        this.runs = runs;
        this.persistenceMapper = persistenceMapper;
        this.reports = reports;
    }

    @Override
    public ProjectFirstValueEvidence verification(String projectId) {
        String id = text(projectId);
        if (id.isBlank() || !runs.available()) return ProjectFirstValueEvidence.none();
        for (AnalysisRunSnapshot run : runs.findRecent(RECENT_LIMIT)) {
            if (run == null || !id.equals(text(run.projectId())) || !"SUCCEEDED".equals(normalize(run.status()))) continue;
            var record = persistenceMapper.record(run);
            OpsAnalysisResponseDTO response = record == null ? null : record.getResponse();
            String executionStyle = record == null || record.getRequest() == null
                    ? ""
                    : normalize(record.getRequest().getExecutionStyle());
            if (response == null || !"REACT".equals(executionStyle)) continue;
            Object diagnosisValue = reports.compose(response).get("diagnosis");
            if (!(diagnosisValue instanceof Map<?, ?> diagnosis)) continue;
            Object factsValue = diagnosis.get("facts");
            if (!(factsValue instanceof List<?> facts) || facts.isEmpty()) continue;
            boolean authoritative = facts.stream().allMatch(this::evidenceBoundFact);
            if (!authoritative) continue;
            return new ProjectFirstValueEvidence(
                    true,
                    run.runId(),
                    "已完成真实 ReAct 诊断并形成 resultId/outputHash 绑定的结构化 Evidence");
        }
        return ProjectFirstValueEvidence.none();
    }

    private boolean evidenceBoundFact(Object value) {
        if (!(value instanceof Map<?, ?> fact)) return false;
        Object refsValue = fact.get("evidenceRefs");
        if (!(refsValue instanceof List<?> refs) || refs.isEmpty()) return false;
        return refs.stream().allMatch(ref -> {
            if (!(ref instanceof Map<?, ?> map)) return false;
            return !text(map.get("evidenceRef")).isBlank()
                    && !text(map.get("resultId")).isBlank()
                    && !text(map.get("outputHash")).isBlank();
        });
    }

    private String normalize(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
