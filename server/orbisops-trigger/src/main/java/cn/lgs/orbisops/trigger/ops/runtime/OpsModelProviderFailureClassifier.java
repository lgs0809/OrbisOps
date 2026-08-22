package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Converts provider failures into stable codes and safe user-facing messages. */
final class OpsModelProviderFailureClassifier {

    private static final Pattern HTTP_UNAUTHORIZED = Pattern.compile("(?s).*\\b(?:401|403)\\b.*");
    private static final Pattern HTTP_RATE_LIMITED = Pattern.compile("(?s).*\\b429\\b.*");
    private static final Pattern HTTP_SERVER_ERROR = Pattern.compile("(?s).*\\b5\\d{2}\\b.*");

    private OpsModelProviderFailureClassifier() {
    }

    static Optional<Failure> classify(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
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
