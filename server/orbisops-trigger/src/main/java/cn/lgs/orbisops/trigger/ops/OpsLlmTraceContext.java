package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Thread-local runtime trace context for custom operations LLM calls.
 */
public final class OpsLlmTraceContext {

    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private OpsLlmTraceContext() {
    }

    public static Trace current() {
        return CURRENT.get();
    }

    public static <T> T withTrace(Trace trace, Supplier<T> supplier) {
        if (trace == null) {
            return supplier.get();
        }
        Trace previous = CURRENT.get();
        CURRENT.set(trace);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> Supplier<T> wrap(Trace trace, Supplier<T> supplier) {
        return () -> withTrace(trace, supplier);
    }

    public record Trace(List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> eventSink,
                        String owner,
                        String nodeId,
                        String nodeType,
                        String agent,
                        String source,
                        cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeSkillFrame skillFrame) {

        public Trace(List<OpsRuntimeEvent> events, Consumer<OpsRuntimeEvent> eventSink,
                     String owner, String nodeId, String nodeType, String agent, String source) {
            this(events, eventSink, owner, nodeId, nodeType, agent, source, null);
        }

        public Trace child(String owner, String nodeId, String nodeType, String agent, String source) {
            return new Trace(events, eventSink, owner, nodeId, nodeType, agent, source, skillFrame);
        }

        public void record(OpsRuntimeEvent event) {
            if (event == null) {
                return;
            }
            if (events != null) {
                synchronized (events) {
                    events.add(event);
                }
            }
            if (eventSink != null) {
                eventSink.accept(event);
            }
        }
    }
}
