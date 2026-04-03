package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Converts provider failures into stable codes and safe user-facing messages. */
public final class OpsModelProviderFailureClassifier {

    private static final Pattern HTTP_UNAUTHORIZED = Pattern.compile("(?s).*\\b(?:401|403)\\b.*");
    private static final Pattern HTTP_RATE_LIMITED = Pattern.compile("(?s).*\\b429\\b.*");
    private static final Pattern HTTP_SERVER_ERROR = Pattern.compile("(?s).*\\b5\\d{2}\\b.*");

    private OpsModelProviderFailureClassifier() {
    }

    /** Shared foreground/background transport boundary; permanent failures stay terminal. */
    public static boolean retryable(Throwable error) {
        return classify(error).filter(f -> f.code().equals("MODEL_PROVIDER_UNAVAILABLE")
                || f.code().equals("MODEL_PROVIDER_RATE_LIMITED")).isPresent();
    }

    static Optional<Failure> classify(Throwable error) {
        if (OpsMcpFailureClassifier.findTyped(error).isPresent()) return Optional.empty();
        if (OpsModelRequestRetry.interrupted(error)) return Optional.empty();
        // A transport wrapper may say "I/O error" around a permanent trust failure.
        // Keep certificate/identity failures out of the transient network retry path.
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof java.security.cert.CertificateException
                    || cause instanceof java.security.cert.CertPathValidatorException
                    || cause instanceof javax.net.ssl.SSLPeerUnverifiedException) return Optional.empty();
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("pkix path building failed") || message.contains("no subject alternative")
                    || message.contains("unable to find valid certification path")) return Optional.empty();
            if (cause.getCause() == cause) break;
        }
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (current instanceof org.springframework.web.reactive.function.client.WebClientResponseException http) {
                var quota = classify(http.getResponseBodyAsString()).filter(f -> f.code().equals("MODEL_PROVIDER_QUOTA_EXHAUSTED"));
                if (quota.isPresent()) return quota;
                int status = http.getStatusCode().value();
                if (status == 408 || status == 425) return Optional.of(unavailable());
            }
            if (current instanceof java.io.EOFException || current instanceof java.net.SocketException
                    || current instanceof java.net.http.HttpTimeoutException) return Optional.of(unavailable());
            if (current instanceof javax.net.ssl.SSLException) {
                String message = String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT);
                if (message.contains("remote host terminated the handshake")
                        || message.contains("ssl peer shut down incorrectly")) return Optional.of(unavailable());
            }
            Optional<Failure> failure = classify(current.getMessage());
            if (failure.isPresent()) {
                return failure;
            }
            String typeName = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (typeName.contains("timeout") || typeName.contains("connect")) {
                return Optional.of(unavailable());
            }
        }
        return Optional.empty();
    }

    static Optional<Failure> classify(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (normalized.contains("model_provider_quota_exhausted")
                || normalized.contains("insufficient_quota")
                || normalized.contains("insufficient_balance")
                || normalized.contains("insufficient account balance")
                || normalized.contains("quota exceeded")) {
            return Optional.of(new Failure(
                    "MODEL_PROVIDER_QUOTA_EXHAUSTED",
                    "模型供应方额度已用尽，请补充额度或切换模型后重试。"));
        }
        if (normalized.contains("model_provider_auth_failed")
                || normalized.contains("invalid api key")
                || normalized.contains("invalid_api_key")
                || normalized.contains("authentication failed")
                || normalized.contains("unauthorized")
                || normalized.contains("forbidden")
                || HTTP_UNAUTHORIZED.matcher(normalized).matches()) {
            return Optional.of(new Failure(
                    "MODEL_PROVIDER_AUTH_FAILED",
                    "模型供应方鉴权失败，请检查当前模型的凭据配置后重试。"));
        }
        if (normalized.contains("model_provider_rate_limited")
                || normalized.contains("rate limit")
                || normalized.contains("rate_limit")
                || HTTP_RATE_LIMITED.matcher(normalized).matches()) {
            return Optional.of(new Failure(
                    "MODEL_PROVIDER_RATE_LIMITED",
                    "模型供应方请求频率受限，请稍后重试或切换模型。"));
        }
        if (normalized.contains("model_provider_unavailable")
                || normalized.equals("model_response_empty")
                || normalized.contains("model_call_timeout")
                || normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("i/o error")
                || normalized.contains("io error")
                || normalized.contains("eof")
                || normalized.contains("unexpected end of stream")
                || normalized.contains("connection reset")
                || normalized.contains("connection refused")
                || normalized.contains("service unavailable")
                || normalized.contains("bad gateway")
                || HTTP_SERVER_ERROR.matcher(normalized).matches()) {
            return Optional.of(unavailable());
        }
        return Optional.empty();
    }

    private static Failure unavailable() {
        return new Failure(
                "MODEL_PROVIDER_UNAVAILABLE",
                "模型供应方暂时不可用，请稍后重试。"
        );
    }

    record Failure(String code, String userMessage) {
        String eventSummary() {
            return code + "：" + userMessage;
        }
    }
}
