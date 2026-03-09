package cn.lgs.orbisops.trigger.http.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Null-safe SSE event writer with one stable failure language. */
public final class OpsSseEventWriter {

    public void sendData(SseEmitter emitter, Object payload) {
        send(emitter, SseEmitter.event().data(payload));
    }

    public void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        if (emitter == null) throw new IllegalArgumentException("SSE_EMITTER_REQUIRED");
        if (event == null) throw new IllegalArgumentException("SSE_EVENT_REQUIRED");
        try {
            emitter.send(event);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "发送 SSE 事件失败：" + summary(error),
                    error);
        }
    }

    private String summary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
