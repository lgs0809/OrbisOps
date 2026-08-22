package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/** Rejects transport/runtime failures that an agent framework returned as plain assistant text. */
public final class OpsAgentOutputGuard {

    private static final Pattern HTTP_ERROR_ENVELOPE = Pattern.compile(
            "(?is)^(?:exception|error):\\s*[45]\\d{2}\\s*-\\s*(?:\\{|\\[|<).*");
    private static final Pattern TRANSPORT_ERROR_ENVELOPE = Pattern.compile(
            "(?is)^(?:exception|error):\\s*(?:i/o error|io error|read timed? out|connect(?:ion)? (?:timed? out|reset|refused)|"
                    + "eof(?: reached)?|ssl(?: handshake)?|tls(?: handshake)?|unexpected end of stream|broken pipe).*");

    private OpsAgentOutputGuard() {
    }

    public static void assertSuccessful(String output) {
        if (!isErrorEnvelope(output)) return;
        String normalized = StringUtils.hasText(output) ? output.trim() : "";
        java.util.Optional<OpsModelProviderFailureClassifier.Failure> failure =
                OpsModelProviderFailureClassifier.classify(normalized);
        if (failure.isPresent()) {
            throw new IllegalStateException(failure.get().code());
        }
        throw new IllegalStateException("AGENT_RUNTIME_ERROR_ENVELOPE");
    }

    public static boolean isErrorEnvelope(String output) {
        String normalized = StringUtils.hasText(output) ? output.trim() : "";
        return HTTP_ERROR_ENVELOPE.matcher(normalized).matches()
                || isTransportErrorEnvelope(normalized)
                || normalized.startsWith("Graph 执行失败：")
                || normalized.startsWith("ReAct 工具流执行失败：");
    }

    public static boolean isTransportErrorEnvelope(String output) {
        String normalized = StringUtils.hasText(output) ? output.trim() : "";
        if (TRANSPORT_ERROR_ENVELOPE.matcher(normalized).matches()) {
            return true;
        }
        return normalized.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(line -> TRANSPORT_ERROR_ENVELOPE.matcher(line).matches());
    }
}
