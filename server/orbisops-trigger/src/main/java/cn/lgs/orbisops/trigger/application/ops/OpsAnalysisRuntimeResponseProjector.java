package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;

import java.util.ArrayList;
import java.util.List;

/** Projects the generic Work Session result into the public analysis response. */
final class OpsAnalysisRuntimeResponseProjector {

    void project(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAgentChatResponse runtimeResponse) {
        String status = runtimeResponse.getMetadata() == null ? ""
                : text(runtimeResponse.getMetadata().get("status"));
        response.setRuntimeStatus(status.isBlank() ? "SUCCEEDED" : status);
        response.setAgentDefinitionId(runtimeResponse.getAgentId());
        response.setAgentVersion(runtimeResponse.getAgentVersion());
        response.setAgentRuntime(runtimeResponse.getEngine());
        response.setAiPrompt(effectiveQuestion(request));
        if (!hasText(response.getMarkdownReport())) {
            response.setMarkdownReport(runtimeResponse.getContent());
        }
        response.setAgentExecutionSteps(projectEvents(runtimeResponse.getEvents()));
        response.setExecutionNotes(List.of(
                "本次运行直接执行所选 Agent 编排；数据源能力来自当前项目中节点绑定的 MCP、RAG 和 Skill。"));
    }

    private List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> projectEvents(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) {
            return new ArrayList<>();
        }
        return events.stream()
                .filter(this::projectable)
                .map(event -> {
                    var payload = event.getPayload() == null ? java.util.Map.<String, Object>of() : event.getPayload();
                    return OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder()
                            .eventType(event.getEventType())
                            .nodeId(event.getNodeId())
                            .nodeType(event.getNodeType())
                            .agent(event.getAgent())
                            .source(event.getSource())
                            .status(event.getStatus())
                            .summary(event.getSummary())
                            .sourceType(sourceType(event, payload))
                            .resultId(text(payload.get("resultId")))
                            .evidenceId(text(payload.get("evidenceId")))
                            .outputHash(text(payload.get("outputHash")))
                            .verified(Boolean.TRUE.equals(payload.get("verified")))
                            .outcome(("REACT_OUTCOME".equals(text(event.getEventType()))
                                    || "WORKFLOW_OUTCOME".equals(text(event.getEventType())))
                                    ? java.util.Map.copyOf(payload)
                                    : java.util.Map.of())
                            .changePackageBehavior(text(payload.get("changePackageBehavior")))
                            .startedAt(event.getTimestamp())
                            .finishedAt(event.getTimestamp())
                            .durationMs(durationMillis(event))
                            .build();
                })
                .toList();
    }

    private boolean projectable(OpsRuntimeEvent event) {
        if (event == null) return false;
        if (event.getNodeId() != null) return true;
        String type = text(event.getEventType());
        return "SOURCE_QUERY_FINISHED".equals(type)
                || "SKILL_CONTEXT_LOADED".equals(type)
                || "REACT_OUTCOME".equals(type)
                || "WORKFLOW_OUTCOME".equals(type)
                || "RAG_RETRIEVE".equals(type)
                || type.startsWith("CHANGE_PACKAGE_")
                || type.startsWith("VERIFICATION_");
    }

    private String sourceType(OpsRuntimeEvent event, java.util.Map<String, Object> payload) {
        String sourceType = text(payload.get("sourceType"));
        if (!sourceType.isBlank()) return sourceType;
        return "RAG_RETRIEVE".equals(text(event.getEventType())) ? "RAG" : "";
    }

    private Long durationMillis(OpsRuntimeEvent event) {
        Object duration = event.getPayload() == null ? null : event.getPayload().get("durationMs");
        return duration instanceof Number number ? number.longValue() : null;
    }

    private String effectiveQuestion(OpsAgentRunRequestDTO request) {
        if (request == null) {
            return null;
        }
        return hasText(request.getQuestion()) ? request.getQuestion() : request.getQuery();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
