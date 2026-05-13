package cn.lgs.orbisops.trigger.ops.rag;

import java.util.Set;

/** Typed and normalized configuration for multimodal RAG embedding. */
public final class RagMultimodalSettings {

    public static final String DEFAULT_PROVIDER = "qwen-vl";
    public static final int DEFAULT_DIMENSION = 2048;

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("voyage", DEFAULT_PROVIDER);
    private static final Set<Integer> SUPPORTED_DIMENSIONS = Set.of(256, 384, 512, 768, 1024, 2048, 3072);
    private static final Set<String> SUPPORTED_IMAGE_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    private final boolean enabled;
    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String path;
    private final String model;
    private final String tableName;
    private final int dimension;
    private final boolean autoInit;
    private final boolean indexTextDocuments;
    private final boolean indexOriginalMedia;
    private final boolean indexPdfPageImages;
    private final int maxPdfPages;
    private final int pdfRenderDpi;
    private final long maxImageBytes;
    private final int maxTextChars;
    private final int defaultSearchTopK;
    private final int timeoutSeconds;
    private final int maxRetries;

    public RagMultimodalSettings(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String path,
            String model,
            String tableName,
            int configuredDimension,
            boolean autoInit,
            boolean indexTextDocuments,
            boolean indexOriginalMedia,
            boolean indexPdfPageImages,
            int maxPdfPages,
            int pdfRenderDpi,
            long maxImageBytes,
            int maxTextChars,
            int defaultSearchTopK,
            int timeoutSeconds,
            int maxRetries) {
        this.enabled = enabled;
        this.provider = normalize(provider).toLowerCase(java.util.Locale.ROOT);
        this.baseUrl = stripTrailingSlash(normalize(baseUrl));
        this.apiKey = normalize(apiKey);
        this.path = stripLeadingSlash(normalize(path));
        this.model = normalize(model);
        this.tableName = normalize(tableName);
        this.dimension = SUPPORTED_DIMENSIONS.contains(configuredDimension)
                ? configuredDimension : DEFAULT_DIMENSION;
        this.autoInit = autoInit;
        this.indexTextDocuments = indexTextDocuments;
        this.indexOriginalMedia = indexOriginalMedia;
        this.indexPdfPageImages = indexPdfPageImages;
        this.maxPdfPages = Math.max(1, maxPdfPages);
        this.pdfRenderDpi = clamp(pdfRenderDpi, 72, 200);
        this.maxImageBytes = maxImageBytes;
        this.maxTextChars = maxTextChars;
        this.defaultSearchTopK = clamp(defaultSearchTopK, 1, 30);
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.maxRetries = Math.max(0, maxRetries);
    }

    public static RagMultimodalSettings defaults() {
        return new RagMultimodalSettings(
                false,
                "",
                "",
                "",
                "v1/multimodalembeddings",
                "",
                "orbisops_multimodal_vectors",
                DEFAULT_DIMENSION,
                true,
                false,
                true,
                true,
                3,
                144,
                20_971_520L,
                3000,
                8,
                30,
                1);
    }

    public boolean enabled() {
        return enabled;
    }

    public String provider() {
        return provider;
    }

    public boolean providerSupported() {
        return SUPPORTED_PROVIDERS.contains(provider);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean baseUrlConfigured() {
        return hasText(baseUrl);
    }

    public String apiKey() {
        return apiKey;
    }

    public boolean apiKeyConfigured() {
        return hasText(apiKey);
    }

    public String path() {
        return path;
    }

    public String endpoint() {
        return baseUrl + "/" + path;
    }

    public String model() {
        return model;
    }

    public String tableName() {
        return tableName;
    }

    public int dimension() {
        return dimension;
    }

    public boolean autoInit() {
        return autoInit;
    }

    public boolean indexTextDocuments() {
        return indexTextDocuments;
    }

    public boolean indexOriginalMedia() {
        return indexOriginalMedia;
    }

    public boolean indexPdfPageImages() {
        return indexPdfPageImages;
    }

    public int maxPdfPages() {
        return maxPdfPages;
    }

    public int pdfRenderDpi() {
        return pdfRenderDpi;
    }

    public long maxImageBytes() {
        return maxImageBytes;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public int defaultSearchTopK() {
        return defaultSearchTopK;
    }

    public int resolveSearchTopK(int requestedTopK) {
        return clamp(requestedTopK > 0 ? requestedTopK : defaultSearchTopK, 1, 30);
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public boolean supportsImageMimeType(String mimeType) {
        return SUPPORTED_IMAGE_MIME_TYPES.contains(mimeType);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/") && normalized.length() > 1) {
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
