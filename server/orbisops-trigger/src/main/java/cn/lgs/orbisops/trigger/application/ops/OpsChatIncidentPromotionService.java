package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.incident.CreateIncidentCommand;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.incident.IncidentDiagnosisQueryPort;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Promotes only authoritative formal Chat diagnoses into Incident.
 * This service never inspects free-text intent to decide whether a Chat is an incident.
 */
@Service
public class OpsChatIncidentPromotionService {

    private final IncidentCommandApplicationService commands;
    private final IncidentQueryApplicationService queries;
    private final IncidentDiagnosisQueryPort diagnoses;
    private final GraphEventApplicationService graphEvents;

    public OpsChatIncidentPromotionService(
            IncidentCommandApplicationService commands,
            IncidentQueryApplicationService queries,
            IncidentDiagnosisQueryPort diagnoses) {
        this(commands, queries, diagnoses, null);
    }

    @Autowired
    public OpsChatIncidentPromotionService(
            IncidentCommandApplicationService commands,
            IncidentQueryApplicationService queries,
            IncidentDiagnosisQueryPort diagnoses,
            GraphEventApplicationService graphEvents) {
        this.commands = commands;
        this.queries = queries;
        this.diagnoses = diagnoses;
        this.graphEvents = graphEvents;
    }

    public Optional<String> promote(OpsAgentChatRequest request, OpsAgentChatResponse response) {
        if (request == null || response == null) return Optional.empty();
        String projectId = text(request.getProjectId());
        String runId = text(request.getRunId());
        String actor = text(request.getUserId());
        if (projectId.isBlank() || runId.isBlank() || actor.isBlank()) return Optional.empty();

        String requestedIncidentId = metadataText(request.getMetadata(), "incidentId");
        if (!requestedIncidentId.isBlank()) {
            IncidentSnapshot existing = queries.get(requestedIncidentId)
                    .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + requestedIncidentId));
            if (!projectId.equals(existing.projectId())) {
                throw new SecurityException("INCIDENT_PROJECT_MISMATCH");
            }
            commands.linkRun(existing.incidentId(), runId, actor, "Chat 继续调查关联分析 Run");
            attach(response, existing.incidentId(), false, "EXISTING_INCIDENT");
            auditPromotion(request, existing.incidentId(), false, "EXISTING_INCIDENT");
            return Optional.of(existing.incidentId());
        }

        Optional<DiagnosisResult> diagnosis = diagnoses.latestByRunIds(List.of(runId));
        if (diagnosis.isEmpty()) {
            diagnosis = runtimeDiagnosis(response);
        }
        if (diagnosis.isEmpty() || !formalDiagnosis(diagnosis.get())) return Optional.empty();

        DiagnosisResult result = diagnosis.get();
        String sessionId = text(request.getSessionId());
        String recurringKey = "chat-formal-diagnosis:" + (sessionId.isBlank() ? runId : sessionId);
        String reason = result.requiresAction() ? "ACTION_REQUIRED" : "MULTI_SOURCE_DIAGNOSIS";
        IncidentSnapshot incident = commands.openRecurring(
                recurringKey,
                new CreateIncidentCommand(
                        projectId,
                        title(request.getQuery()),
                        "OPEN",
                        result.requiresAction() ? "WARNING" : "INFO",
                        "",
                        "CHAT",
                        result.summary(),
                        Map.of("source", "CHAT", "formalDiagnosis", true),
                        Map.of(
                                "sessionId", sessionId,
                                "promotionReason", reason,
                                "sourceRunId", runId),
                        result.impact()),
                actor);
        commands.linkRun(incident.incidentId(), runId, actor, "结构化正式诊断自动归档到 Incident");
        attach(response, incident.incidentId(), true, reason);
        auditPromotion(request, incident.incidentId(), true, reason);
        return Optional.of(incident.incidentId());
    }

    private boolean formalDiagnosis(DiagnosisResult diagnosis) {
        if (diagnosis.requiresAction()) return true;
        if (diagnosis.facts().isEmpty()
                || diagnosis.evidenceCompleteness() == DiagnosisResult.EvidenceCompleteness.INSUFFICIENT) {
            return false;
        }
        long successfulSources = diagnosis.sourceStatus().stream()
                .filter(source -> source.queryStatus() == DiagnosisResult.SourceQueryStatus.SUCCEEDED)
                .count();
        return successfulSources >= 2;
    }

    private Optional<DiagnosisResult> runtimeDiagnosis(OpsAgentChatResponse response) {
        if (response == null || response.getEvents() == null || response.getEvents().isEmpty()) {
            return Optional.empty();
        }
        OpsRuntimeEvent outcomeEvent = response.getEvents().stream()
                .filter(event -> event != null
                        && "REACT_OUTCOME".equals(text(event.getEventType()))
                        && "SUCCEEDED".equalsIgnoreCase(text(event.getStatus())))
                .reduce((first, second) -> second)
                .orElse(null);
        if (outcomeEvent == null || outcomeEvent.getPayload() == null) {
            return Optional.empty();
        }

        Map<String, Object> outcome = outcomeEvent.getPayload();
        boolean requiresAction = bool(outcome.get("requiresAction"));
        DiagnosisResult.EvidenceCompleteness completeness = evidenceCompleteness(
                outcome.get("evidenceCompleteness"));

        List<DiagnosisResult.Fact> facts = new ArrayList<>();
        List<DiagnosisResult.SourceStatus> sources = new ArrayList<>();
        int factIndex = 0;
        for (OpsRuntimeEvent event : response.getEvents()) {
            if (!authoritativeSourceEvent(event)) continue;
            Map<String, Object> payload = event.getPayload();
            String sourceType = text(payload.get("sourceType")).toLowerCase(Locale.ROOT);
            String resultId = text(payload.get("resultId"));
            String outputHash = text(payload.get("outputHash"));
            if (sourceType.isBlank() || resultId.isBlank() || outputHash.isBlank()) continue;
            factIndex++;
            String evidenceRef = text(payload.get("evidenceId"));
            if (evidenceRef.isBlank()) evidenceRef = "runtime-" + sourceType + "-" + factIndex;
            facts.add(new DiagnosisResult.Fact(
                    "chat-runtime-" + sourceType + "-" + factIndex,
                    sourceType + " authoritative query completed",
                    List.of(new DiagnosisResult.EvidenceRef(evidenceRef, resultId, outputHash))));
            sources.add(new DiagnosisResult.SourceStatus(
                    sourceType,
                    sourceType,
                    DiagnosisResult.SourceQueryStatus.SUCCEEDED,
                    DiagnosisResult.SourceAssessment.UNKNOWN,
                    DiagnosisResult.SourceState.UNKNOWN,
                    text(event.getSummary())));
        }
        if (facts.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new DiagnosisResult(
                "Chat ReAct 结构化诊断已完成",
                List.of(),
                facts,
                List.of(),
                List.of(),
                completeness == DiagnosisResult.EvidenceCompleteness.INSUFFICIENT
                        ? List.of("当前权威证据仍不足以闭环结论")
                        : List.of(),
                List.of(),
                sources,
                completeness,
                completeness == DiagnosisResult.EvidenceCompleteness.COMPLETE
                        ? DiagnosisResult.Confidence.HIGH
                        : DiagnosisResult.Confidence.MEDIUM,
                requiresAction,
                requiresAction ? "继续受控运维处置" : "继续观察"));
    }

    private boolean authoritativeSourceEvent(OpsRuntimeEvent event) {
        if (event == null
                || !"SOURCE_QUERY_FINISHED".equals(text(event.getEventType()))
                || !"SUCCEEDED".equalsIgnoreCase(text(event.getStatus()))
                || event.getPayload() == null) {
            return false;
        }
        Map<String, Object> payload = event.getPayload();
        return Boolean.TRUE.equals(payload.get("verified"))
                && !text(payload.get("sourceType")).isBlank()
                && !text(payload.get("resultId")).isBlank()
                && !text(payload.get("outputHash")).isBlank();
    }

    private DiagnosisResult.EvidenceCompleteness evidenceCompleteness(Object value) {
        try {
            return DiagnosisResult.EvidenceCompleteness.valueOf(text(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DiagnosisResult.EvidenceCompleteness.INSUFFICIENT;
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(text(value));
    }

    private void attach(OpsAgentChatResponse response, String incidentId, boolean promoted, String reason) {
        Map<String, Object> metadata = response.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(response.getMetadata());
        metadata.put("incidentId", incidentId);
        metadata.put("formalDiagnosisPromoted", promoted);
        metadata.put("formalDiagnosisPromotionReason", reason);
        response.setMetadata(metadata);
    }

    private void auditPromotion(
            OpsAgentChatRequest request,
            String incidentId,
            boolean promoted,
            String reason) {
        if (graphEvents == null || request == null || text(request.getRunId()).isBlank()) return;
        graphEvents.publish(
                text(request.getRunId()),
                text(request.getSessionId()),
                "INCIDENT_PROMOTION_FINISHED",
                null,
                "SUCCEEDED",
                promoted ? "正式诊断已归档到 Incident。" : "Chat Run 已关联到既有 Incident。",
                null,
                null,
                null,
                Map.of(
                        "incidentId", text(incidentId),
                        "formalDiagnosisPromoted", promoted,
                        "formalDiagnosisPromotionReason", text(reason)));
    }

    private String title(String query) {
        String normalized = text(query).replaceAll("\\s+", " ");
        if (normalized.isBlank()) return "AI 正式诊断";
        return "AI 诊断：" + (normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "…");
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) return "";
        return text(metadata.get(key));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
