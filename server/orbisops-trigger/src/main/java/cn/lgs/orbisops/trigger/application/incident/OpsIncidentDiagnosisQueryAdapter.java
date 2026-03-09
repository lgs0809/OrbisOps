package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.incident.IncidentDiagnosisQueryPort;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads the latest authoritative structured diagnosis from persisted linked analysis runs. */
public final class OpsIncidentDiagnosisQueryAdapter implements IncidentDiagnosisQueryPort {

    private final IAnalysisRunRepository runs;
    private final OpsAnalysisRunPersistenceMapper persistenceMapper;
    private final OpsStructuredReportService reports;

    public OpsIncidentDiagnosisQueryAdapter(
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
    public Optional<DiagnosisResult> latestByRunIds(List<String> runIds) {
        if (!runs.available() || runIds == null || runIds.isEmpty()) return Optional.empty();
        for (String runId : runIds) {
            if (text(runId).isBlank()) continue;
            Optional<DiagnosisResult> diagnosis = runs.find(runId)
                    .map(persistenceMapper::record)
                    .map(record -> record == null ? null : record.getResponse())
                    .map(this::diagnosis);
            if (diagnosis.isPresent()) return diagnosis;
        }
        return Optional.empty();
    }

    private DiagnosisResult diagnosis(OpsAnalysisResponseDTO response) {
        if (response == null) return null;
        Object value = reports.compose(response).get("diagnosis");
        if (!(value instanceof Map<?, ?> raw)) return null;
        Map<String, Object> map = stringMap(raw);
        try {
            return new DiagnosisResult(
                    text(map.get("summary")),
                    strings(map.get("impact")),
                    facts(map.get("facts")),
                    inferences(map.get("inferences")),
                    strings(map.get("excludedHypotheses")),
                    strings(map.get("unknowns")),
                    strings(map.get("recommendations")),
                    sourceStatuses(map.get("sourceStatus")),
                    enumValue(DiagnosisResult.EvidenceCompleteness.class,
                            map.get("evidenceCompleteness"), DiagnosisResult.EvidenceCompleteness.INSUFFICIENT),
                    enumValue(DiagnosisResult.Confidence.class,
                            map.get("confidence"), DiagnosisResult.Confidence.LOW),
                    bool(map.get("requiresAction")),
                    text(map.get("suggestedNextAction")));
        } catch (RuntimeException invalidStructuredDiagnosis) {
            // Never coerce malformed/free-text data into a trusted DiagnosisResult.
            return null;
        }
    }

    private List<DiagnosisResult.Fact> facts(Object value) {
        List<DiagnosisResult.Fact> result = new ArrayList<>();
        for (Map<String, Object> item : maps(value)) {
            List<DiagnosisResult.EvidenceRef> refs = new ArrayList<>();
            for (Map<String, Object> ref : maps(item.get("evidenceRefs"))) {
                refs.add(new DiagnosisResult.EvidenceRef(
                        text(ref.get("evidenceRef")),
                        text(ref.get("resultId")),
                        text(ref.get("outputHash"))));
            }
            result.add(new DiagnosisResult.Fact(
                    text(item.get("factId")),
                    text(item.get("statement")),
                    refs));
        }
        return List.copyOf(result);
    }

    private List<DiagnosisResult.Inference> inferences(Object value) {
        List<DiagnosisResult.Inference> result = new ArrayList<>();
        for (Map<String, Object> item : maps(value)) {
            result.add(new DiagnosisResult.Inference(
                    text(item.get("statement")),
                    strings(item.get("supports"))));
        }
        return List.copyOf(result);
    }

    private List<DiagnosisResult.SourceStatus> sourceStatuses(Object value) {
        List<DiagnosisResult.SourceStatus> result = new ArrayList<>();
        for (Map<String, Object> item : maps(value)) {
            result.add(new DiagnosisResult.SourceStatus(
                    text(item.get("sourceId")),
                    text(item.get("sourceName")),
                    enumValue(DiagnosisResult.SourceQueryStatus.class,
                            item.get("queryStatus"), DiagnosisResult.SourceQueryStatus.NOT_QUERIED),
                    enumValue(DiagnosisResult.SourceAssessment.class,
                            item.get("assessment"), DiagnosisResult.SourceAssessment.UNKNOWN),
                    enumValue(DiagnosisResult.SourceState.class,
                            item.get("state"), DiagnosisResult.SourceState.UNKNOWN),
                    text(item.get("detail"))));
        }
        return List.copyOf(result);
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) result.add(stringMap(map));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) result.put(String.valueOf(key), value);
        });
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::text).filter(item -> !item.isBlank()).toList();
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(text(value));
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, Object value, E fallback) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
