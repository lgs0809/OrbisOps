package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Builds the runtime work-session request and its analysis metadata envelope. */
final class OpsAnalysisRuntimeRequestFactory {

    private final OpsAnalysisAgentDefinitionSnapshotResolver snapshotResolver;

    OpsAnalysisRuntimeRequestFactory(OpsAnalysisAgentDefinitionSnapshotResolver snapshotResolver) {
        this.snapshotResolver = snapshotResolver;
    }

    OpsAgentChatRequest create(OpsAgentRunRequestDTO request, OpsAnalysisResponseDTO response) {
        OpsAgentDefinition snapshot = snapshotResolver.parse(request.getAgentDefinitionSnapshotJson());
        String question = effectiveQuestion(request);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggerSource", hasText(request.getTriggerSource())
                ? request.getTriggerSource()
                : "ops-analysis");
        metadata.put("_trustedTriggerSource", trustedTriggerSource(request.getTriggerSource()));
        metadata.put("executionStyle", executionStyle(request));
        metadata.put("triggerEventId", hasText(request.getTriggerEventId())
                ? request.getTriggerEventId()
                : "");
        metadata.put(WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST, request);
        metadata.put(WorkSessionMetadataKeys.OPS_ANALYSIS_RESPONSE, response);
        metadata.put(WorkSessionMetadataKeys.OPS_ANALYSIS_QUESTION_CONTEXT, OpsQuestionContext.from(question));
        return OpsAgentChatRequest.builder()
                .runId(request.getRunId())
                .userId(hasText(request.getRequestedBy()) ? request.getRequestedBy() : "ops-analysis")
                .sessionId(hasText(request.getRunId())
                        ? request.getRunId()
                        : "ops_" + System.currentTimeMillis())
                .projectId(request.getProjectId())
                .query(hasText(question) ? question : "分析当前业务系统最近运行状态")
                .mode("WORKFLOW".equals(executionStyle(request)) ? "WORKFLOW" : "AGENT")
                .agentDefinitionId(snapshot == null ? request.getAgentDefinitionId() : snapshot.getAgentId())
                .agentVersion(snapshot == null ? request.getAgentVersion() : snapshot.getVersion())
                .agentDefinition(snapshot)
                .metadata(metadata)
                .build();
    }

    String effectiveQuestion(OpsAgentRunRequestDTO request) {
        if (request == null) return null;
        return hasText(request.getQuestion()) ? request.getQuestion() : request.getQuery();
    }

    private String executionStyle(OpsAgentRunRequestDTO request) {
        String style = request == null ? "" : text(request.getExecutionStyle()).toUpperCase(Locale.ROOT);
        return "WORKFLOW".equals(style) ? "WORKFLOW" : "REACT";
    }

    private TriggerSource trustedTriggerSource(String source) {
        String value = text(source).toLowerCase(Locale.ROOT);
        if (value.contains("schedule") || value.contains("task")) return TriggerSource.SCHEDULE;
        if (value.contains("inspection")) return TriggerSource.INSPECTION;
        if (value.contains("alert")) return TriggerSource.ALERT;
        return TriggerSource.API;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
