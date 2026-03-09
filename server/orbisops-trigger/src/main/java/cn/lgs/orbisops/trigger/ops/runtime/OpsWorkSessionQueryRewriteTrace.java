package cn.lgs.orbisops.trigger.ops.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Pure trace-payload support for Work Session query rewrite orchestration. */
final class OpsWorkSessionQueryRewriteTrace {

    private static final int TRACE_PREVIEW_CHARS = 1200;

    private OpsWorkSessionQueryRewriteTrace() {
    }

    static void record(List<OpsRuntimeEvent> events,
                       Consumer<OpsRuntimeEvent> sink,
                       OpsRuntimeEvent event) {
        events.add(event);
        if (sink != null) sink.accept(event);
    }

    static Map<String, Object> payloadWithElapsed(Map<String, Object> seed, long startedNanos) {
        Map<String, Object> payload = new LinkedHashMap<>(seed == null ? Map.of() : seed);
        payload.put("elapsedMs", (System.nanoTime() - startedNanos) / 1_000_000);
        return payload;
    }

    static Map<String, Object> questionTrace(String field, String question, Map<String, Object> seed) {
        String normalized = value(question);
        Map<String, Object> payload = new LinkedHashMap<>(seed == null ? Map.of() : seed);
        payload.put(field, preview(normalized));
        payload.put(field + "Chars", normalized.length());
        payload.put(field + "Hash", sha256(normalized));
        return payload;
    }

    static String preview(String value) {
        String normalized = value(value);
        return normalized.length() <= TRACE_PREVIEW_CHARS
                ? normalized
                : normalized.substring(0, TRACE_PREVIEW_CHARS) + "…";
    }

    static String value(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
