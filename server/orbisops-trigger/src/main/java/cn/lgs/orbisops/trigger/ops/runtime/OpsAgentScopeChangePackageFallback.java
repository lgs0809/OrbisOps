package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Converts a successful PrepareChangePackage fact into a bounded final-answer fallback. */
final class OpsAgentScopeChangePackageFallback {

    private final OpsAgentScopeOutputReader outputReader;

    OpsAgentScopeChangePackageFallback(OpsAgentScopeOutputReader outputReader) {
        this.outputReader = outputReader;
    }

    Optional<String> recoverFinalAnswerFailure(
            Throwable error,
            List<OpsRuntimeEvent> events,
            int eventOffset,
            Consumer<OpsRuntimeEvent> eventSink,
            String agentName) {
        if (!isEmptyFinalAnswerFailure(error) || !hasPreparedSince(events, eventOffset)) {
            return Optional.empty();
        }
        String output = preparedOutput(events, eventOffset);
        recordDegraded(events, eventSink, agentName);
        return Optional.of(output);
    }

    Optional<String> recoverEmptyOutput(
            String rawOutput,
            List<OpsRuntimeEvent> events,
            int eventOffset,
            Consumer<OpsRuntimeEvent> eventSink,
            String agentName) {
        if (outputReader.isMeaningfulText(rawOutput) || !hasPreparedSince(events, eventOffset)) {
            return Optional.empty();
        }
        String output = preparedOutput(events, eventOffset);
        recordDegraded(events, eventSink, agentName);
        return Optional.of(output);
    }

    private boolean hasPreparedSince(List<OpsRuntimeEvent> events, int eventOffset) {
        return snapshot(events, eventOffset).stream().anyMatch(event -> event != null
                && "CHANGE_PACKAGE_PREPARED".equals(event.getEventType())
                && "SUCCEEDED".equalsIgnoreCase(event.getStatus())
                && prepared(event) != null);
    }

    private String preparedOutput(List<OpsRuntimeEvent> events, int eventOffset) {
        OpsRuntimeEvent prepared = snapshot(events, eventOffset).stream()
                .filter(event -> event != null
                        && "CHANGE_PACKAGE_PREPARED".equals(event.getEventType())
                        && "SUCCEEDED".equalsIgnoreCase(event.getStatus())
                        && prepared(event) != null)
                .reduce((first, latest) -> latest)
                .orElse(null);
        return prepared(prepared);
    }

    private String prepared(OpsRuntimeEvent event) {
        return event == null || event.getPayload() == null ? null
                : OpsPreparedChangePackageSummary.render(event.getPayload().get("output"));
    }

    private void recordDegraded(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String agentName) {
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("REACT_EMPTY_OUTPUT_DEGRADED")
                .nodeType("AGENTSCOPE_OUTCOME")
                .agent(agentName)
                .status("DEGRADED")
                .summary("最终模型未返回文本，已使用可信 ChangePackage 结果补齐回复。")
                .build();
        events.add(event);
        if (eventSink != null) eventSink.accept(event);
    }

    private boolean isEmptyFinalAnswerFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (text(current.getMessage()).contains("Empty flux detected for key 'final_answer'")) {
                return true;
            }
        }
        return false;
    }

    private List<OpsRuntimeEvent> snapshot(List<OpsRuntimeEvent> events, int eventOffset) {
        if (events == null || events.isEmpty()) return List.of();
        synchronized (events) {
            int start = Math.max(0, Math.min(eventOffset, events.size()));
            return List.copyOf(events.subList(start, events.size()));
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
