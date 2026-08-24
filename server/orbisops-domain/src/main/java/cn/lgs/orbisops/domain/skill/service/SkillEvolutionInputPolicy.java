package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInput;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionMessage;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionTraceEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Domain rules for summarizing neutral Skill Evolution trace and conversation input. */
public class SkillEvolutionInputPolicy {

    private static final Set<String> FINAL_EVENT_TYPES = Set.of("FINAL_OUTPUT", "RUN_FINISHED", "DONE");
    private static final int MAX_EVENT_SUMMARIES = 80;
    private static final int MAX_TOOL_EVENTS = 20;
    private static final int MAX_USER_GOAL_LENGTH = 1000;

    public SkillEvolutionInputSummary summarize(SkillEvolutionInput input) {
        SkillEvolutionInput source = input == null
                ? new SkillEvolutionInput(List.of(), List.of())
                : input;

        List<String> eventSummaries = source.trace().stream()
                .map(event -> event.eventType() + ":" + event.status() + ":" + event.summary())
                .filter(this::hasText)
                .limit(MAX_EVENT_SUMMARIES)
                .toList();

        List<String> toolEvents = source.trace().stream()
                .filter(event -> event.eventType().toUpperCase(Locale.ROOT).contains("TOOL"))
                .map(event -> fallback(event.summary(), event.eventType()))
                .filter(this::hasText)
                .limit(MAX_TOOL_EVENTS)
                .toList();

        List<SkillEvolutionTraceEvent> finalEvents = source.trace().stream()
                .filter(event -> FINAL_EVENT_TYPES.contains(event.eventType().toUpperCase(Locale.ROOT)))
                .toList();
        // FINAL_OUTPUT/NODE_END carry the actual user-visible answer in content,
        // while DONE/RUN_FINISHED intentionally carry only a short lifecycle
        // summary. Prefer content across all terminal events so a later generic
        // DONE event cannot erase the reusable report needed by the evolver.
        String finalContent = lastText(finalEvents.stream()
                .map(SkillEvolutionTraceEvent::content)
                .toList());
        String finalReport = finalContent;
        String terminalSummary = lastText(finalEvents.stream()
                .map(event -> firstText(event.summary(), text(event.payload())))
                .toList());
        if (finalReport.isBlank()) finalReport = terminalSummary;
        if (isGenericFinalSummary(finalReport)) {
            String assistantReport = source.messages().stream()
                    .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                    .map(SkillEvolutionMessage::content)
                    .filter(this::hasText)
                    .reduce((left, right) -> right)
                    .orElse("");
            if (hasText(assistantReport)) finalReport = abbreviate(assistantReport, MAX_USER_GOAL_LENGTH);
        }

        boolean hasCompleted = source.trace().stream().anyMatch(event ->
                "SUCCEEDED".equalsIgnoreCase(event.status())
                        || "DONE".equalsIgnoreCase(event.eventType()));

        String messageUserGoal = source.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(SkillEvolutionMessage::content)
                .filter(this::hasText)
                .reduce((left, right) -> right)
                .map(value -> abbreviate(value, MAX_USER_GOAL_LENGTH))
                .orElse("");
        String traceUserGoal = source.trace().stream()
                .filter(this::userInputEvent)
                .map(SkillEvolutionTraceEvent::payload)
                .map(this::traceUserGoal)
                .filter(this::hasText)
                .findFirst()
                .map(value -> abbreviate(value, MAX_USER_GOAL_LENGTH))
                .orElse("");
        String normalizedUserGoal = hasText(messageUserGoal) ? messageUserGoal : traceUserGoal;
        if (hasText(source.acceptedGoal())) normalizedUserGoal = source.acceptedGoal();
        boolean hasMessages = !source.messages().isEmpty() || hasText(traceUserGoal);

        List<SkillEvolutionEvidenceReference> evidenceReferences = evidenceReferences(source.trace());
        String contextBundleHash = contextBundleHash(source.trace());

        return new SkillEvolutionInputSummary(
                eventSummaries,
                toolEvents,
                normalizedUserGoal,
                finalReport,
                hasCompleted,
                !toolEvents.isEmpty(),
                hasMessages,
                evidenceReferences,
                contextBundleHash,
                source.episodeJson(),
                source.sourceHash());
    }

    /** Cheap deterministic gate executed before any Skill Evolution LLM pipeline call. */
    public String skipReason(SkillEvolutionInputSummary summary) {
        if (summary == null || !summary.hasCompleted()) return "SKIP_INCOMPLETE_RUN";
        if (!summary.hasMessages()) return "SKIP_NO_CONVERSATION";
        if (!summary.hasToolEvidence() && summary.evidenceReferences().isEmpty()) {
            return "SKIP_NO_TOOL_EVIDENCE";
        }
        return "";
    }

    public String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private boolean userInputEvent(SkillEvolutionTraceEvent event) {
        if (event == null) return false;
        String type = text(event.eventType()).toUpperCase(Locale.ROOT);
        return type.equals("RUN_STARTED")
                || type.equals("NODE_STARTED")
                || type.equals("USER_INPUT")
                || type.equals("USER_MESSAGE");
    }

    private String traceUserGoal(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "";
        for (String key : List.of("question", "query", "userGoal", "userInput", "objective")) {
            String value = text(payload.get(key));
            if (hasText(value)) return value;
        }
        return "";
    }

    private List<SkillEvolutionEvidenceReference> evidenceReferences(List<SkillEvolutionTraceEvent> trace) {
        List<SkillEvolutionEvidenceReference> references = new ArrayList<>();
        for (SkillEvolutionTraceEvent event : trace) {
            Map<String, Object> payload = event.payload();
            if (payload.get("evidenceId") != null
                    && payload.get("resultId") != null
                    && payload.get("outputHash") != null) {
                references.add(new SkillEvolutionEvidenceReference(
                        text(payload.get("evidenceId")),
                        text(payload.get("resultId")),
                        text(payload.get("outputHash"))));
            }
        }
        return List.copyOf(references);
    }

    private String contextBundleHash(List<SkillEvolutionTraceEvent> trace) {
        return trace.stream()
                .map(SkillEvolutionTraceEvent::payload)
                .map(payload -> text(payload.get("contextBundleHash")))
                .filter(this::hasText)
                .findFirst()
                .orElse("");
    }

    private String fallback(String value, String fallback) {
        return hasText(value) ? value : text(fallback);
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    private String lastText(List<String> values) {
        if (values == null) return "";
        for (int index = values.size() - 1; index >= 0; index--) {
            String value = values.get(index);
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    private boolean isGenericFinalSummary(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() || "Agent 输出完成。".equals(normalized);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
