package cn.lgs.orbisops.trigger.http;

import java.util.Map;

/** Stable SSE failure projection that never emits a null summary. */
public final class OpsSseFailurePayload {

    private static final String DEFAULT_SUMMARY = "Agent 流式处理失败";

    private OpsSseFailurePayload() {
    }

    public static Map<String, Object> failed(Throwable failure) {
        return Map.of(
                "eventType", "ERROR",
                "status", "FAILED",
                "summary", summary(failure));
    }

    public static String summary(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = text(current.getMessage());
            if (!message.isBlank()) return message;
            current = current.getCause();
        }
        if (failure != null) {
            String type = text(failure.getClass().getSimpleName());
            if (!type.isBlank()) return type;
        }
        return DEFAULT_SUMMARY;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
