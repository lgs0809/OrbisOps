package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Records foreground Skill usage facts only; automatic learning runs out of band. */
@Slf4j
final class OpsRuntimeSkillLearningCoordinator {

    private final OpsRuntimeEventJournal eventJournal;
    private final Supplier<OpsSkillRuntimeUsageRecorder> usageRecorderSupplier;

    OpsRuntimeSkillLearningCoordinator(OpsRuntimeEventJournal eventJournal,
                                       Supplier<OpsSkillRuntimeUsageRecorder> usageRecorderSupplier) {
        this.eventJournal = eventJournal;
        this.usageRecorderSupplier = usageRecorderSupplier;
    }

    void recordUsage(OpsAgentChatRequest request,
                     OpsAgentDefinition definition,
                     List<OpsRuntimeEvent> events,
                     String terminalStatus) {
        try {
            recordUsageInternal(request, definition, events, terminalStatus);
        } catch (RuntimeException error) {
            log.warn("Skill usage sidecar failed and was isolated from foreground runtime. runId={} error={}",
                    eventJournal.canonicalRunId(request),
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void recordUsageInternal(OpsAgentChatRequest request,
                                     OpsAgentDefinition definition,
                                     List<OpsRuntimeEvent> events,
                                     String terminalStatus) {
        OpsSkillRuntimeUsageRecorder usageRecorder = usageRecorder();
        if (usageRecorder == null || request == null || request.getMetadata() == null) {
            return;
        }
        Object refsValue = request.getMetadata().get("usedSkillVersionRefs");
        if (!(refsValue instanceof List<?> list)) {
            return;
        }
        List<Map<String, Object>> refs = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        if (refs.isEmpty()) {
            return;
        }
        long toolCalls = events == null ? 0 : events.stream()
                .filter(event -> event != null && value(event.getEventType()).contains("TOOL_CALL"))
                .count();
        long blocked = events == null ? 0 : events.stream()
                .filter(event -> event != null && "BLOCKED".equalsIgnoreCase(value(event.getStatus())))
                .count();
        boolean evidence = hasUsefulEvidence(events);
        boolean needsReplan = hasEventFact(events, "NEEDS_REPLAN");
        boolean changePackageCreated = hasEventFact(events, "CHANGE_PACKAGE_CREATED")
                || hasEventFact(events, "CHANGE_PACKAGE_PREPARED");
        boolean negativeFeedback = Boolean.TRUE.equals(request.getMetadata().get("userNegativeFeedback"));
        boolean success = "SUCCEEDED".equalsIgnoreCase(value(terminalStatus)) && !needsReplan;
        usageRecorder.record(
                value(request.getProjectId()),
                value(definition == null ? null : definition.getAgentId()),
                eventJournal.canonicalRunId(request),
                value(request.getMetadata().get("contextBundleHash")),
                refs,
                Map.of(
                        "terminalStatus", value(terminalStatus),
                        "success", success,
                        "evidenceSufficient", evidence,
                        "toolCallCount", toolCalls,
                        "blockedToolCallCount", blocked,
                        "needsReplan", needsReplan,
                        "replan", needsReplan,
                        "changePackageCreated", changePackageCreated,
                        "userNegativeFeedback", negativeFeedback));
    }

    boolean hasEventFact(List<OpsRuntimeEvent> events, String fact) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        String expected = value(fact).toUpperCase(Locale.ROOT);
        return events.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(event -> {
                    String combined = (value(event.getEventType()) + " "
                            + value(event.getStatus()) + " "
                            + value(event.getSummary())).toUpperCase(Locale.ROOT);
                    return combined.contains(expected);
                });
    }

    boolean hasUsefulEvidence(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(event -> {
            String eventType = value(event == null ? null : event.getEventType()).toUpperCase(Locale.ROOT);
            String status = value(event == null ? null : event.getStatus()).toUpperCase(Locale.ROOT);
            if (!("SUCCEEDED".equals(status) || "FOUND".equals(status))
                    || !(eventType.contains("TOOL")
                    || eventType.contains("EVIDENCE")
                    || eventType.contains("PREFLIGHT")
                    || eventType.contains("DRY_RUN")
                    || eventType.contains("SANDBOX")
                    || eventType.contains("TEST"))) {
                return false;
            }
            Map<String, Object> payload = event == null || event.getPayload() == null
                    ? Map.of()
                    : event.getPayload();
            if (StringUtils.hasText(textValue(payload.get("resultId")))
                    && StringUtils.hasText(textValue(payload.get("outputHash")))) {
                return true;
            }
            Object output = payload.get("output");
            if (output == null) {
                return false;
            }
            try {
                Object parsed = output instanceof String text ? JSON.parse(text) : output;
                if (parsed instanceof Map<?, ?> map) {
                    return StringUtils.hasText(textValue(map.get("resultId")))
                            && StringUtils.hasText(textValue(map.get("outputHash")));
                }
            } catch (RuntimeException ignored) {
                return false;
            }
            return false;
        });
    }

    private OpsSkillRuntimeUsageRecorder usageRecorder() {
        return usageRecorderSupplier == null ? null : usageRecorderSupplier.get();
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
