package cn.lgs.orbisops.trigger.ops.rag;

import java.util.Locale;

/** Typed configuration for visual document analysis. */
public record RagVisualAnalysisSettings(
        boolean enabled,
        boolean highValueOnly,
        String provider,
        String baseUrl,
        String apiKey,
        String path,
        String model,
        String detail,
        int timeoutSeconds,
        int maxCompletionTokens,
        String tokenLimitField,
        String responseFormatMode,
        int maxRetries,
        int maxImagesPerDocument,
        long maxImageBytes,
        int pdfRenderDpi) {

    public static final String PROVIDER_OPENAI = "openai";

    public RagVisualAnalysisSettings {
        provider = normalize(provider, PROVIDER_OPENAI).toLowerCase(Locale.ROOT);
        baseUrl = stripTrailingSlash(normalize(baseUrl, ""));
        apiKey = normalize(apiKey, "");
        path = stripLeadingSlash(normalize(path, "v1/chat/completions"));
        model = normalize(model, "");
        detail = normalize(detail, "low");
        timeoutSeconds = Math.max(5, timeoutSeconds);
        maxCompletionTokens = Math.max(300, Math.min(4000, maxCompletionTokens));
        tokenLimitField = normalize(tokenLimitField, "max_completion_tokens");
        responseFormatMode = normalize(responseFormatMode, "json_schema").toLowerCase(Locale.ROOT);
        maxRetries = Math.max(0, maxRetries);
        maxImagesPerDocument = Math.max(1, maxImagesPerDocument);
        pdfRenderDpi = Math.max(72, Math.min(200, pdfRenderDpi));
    }

    public static RagVisualAnalysisSettings defaults() {
        return new RagVisualAnalysisSettings(
                false,
                true,
                PROVIDER_OPENAI,
                "",
                "",
                "v1/chat/completions",
                "",
                "low",
                30,
                1200,
                "max_completion_tokens",
                "json_schema",
                1,
                3,
                4_194_304L,
                144);
    }

    public boolean providerSupported() {
        return PROVIDER_OPENAI.equals(provider);
    }

    public String endpoint() {
        return baseUrl + "/" + path;
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/") && !normalized.isEmpty()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String stripLeadingSlash(String value) {
        String normalized = value;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
