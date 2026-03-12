package cn.lgs.orbisops.types.common;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public final class TraceContext {

    public static final String MDC_TRACE_ID = "trace-id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String LEGACY_HEADER_TRACE_ID = "trace-id";

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private TraceContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    public static String resolveTraceId(String... candidates) {
        if (candidates != null) {
            for (String candidate : candidates) {
                if (isValid(candidate)) {
                    return candidate.trim();
                }
            }
        }
        return createTraceId();
    }

    public static String createTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void putTraceId(String traceId) {
        MDC.put(MDC_TRACE_ID, resolveTraceId(traceId));
    }

    public static void clearTraceId() {
        MDC.remove(MDC_TRACE_ID);
    }

    public static Runnable wrap(Runnable delegate) {
        String traceId = currentTraceId();
        return () -> runWithTraceId(traceId, delegate);
    }

    public static <V> Callable<V> wrap(Callable<V> delegate) {
        String traceId = currentTraceId();
        return () -> callWithTraceId(traceId, delegate);
    }

    private static void runWithTraceId(String traceId, Runnable delegate) {
        String previousTraceId = currentTraceId();
        try {
            setOrClear(traceId);
            delegate.run();
        } finally {
            setOrClear(previousTraceId);
        }
    }

    private static <V> V callWithTraceId(String traceId, Callable<V> delegate) throws Exception {
        String previousTraceId = currentTraceId();
        try {
            setOrClear(traceId);
            return delegate.call();
        } finally {
            setOrClear(previousTraceId);
        }
    }

    private static void setOrClear(String traceId) {
        if (traceId == null) {
            clearTraceId();
        } else {
            MDC.put(MDC_TRACE_ID, traceId);
        }
    }

    private static boolean isValid(String traceId) {
        return traceId != null && SAFE_TRACE_ID.matcher(traceId.trim()).matches();
    }

}
