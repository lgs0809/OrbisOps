package cn.lgs.orbisops.domain.runtime.taskcontext.model;

public record TaskContextEventFact(
        String eventType,
        String status,
        String summary,
        String content) {

    public TaskContextEventFact {
        eventType = text(eventType);
        status = text(status);
        summary = text(summary);
        content = text(content);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
