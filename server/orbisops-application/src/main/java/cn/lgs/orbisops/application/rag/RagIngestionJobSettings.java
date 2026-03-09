package cn.lgs.orbisops.application.rag;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public record RagIngestionJobSettings(
        int maxFileCount,
        long maxFileBytes,
        long maxTotalBytes,
        Set<String> allowedExtensions,
        Set<String> allowedContentTypes
) {

    public RagIngestionJobSettings {
        maxFileCount = Math.max(1, maxFileCount);
        maxFileBytes = Math.max(1L, maxFileBytes);
        maxTotalBytes = Math.max(1L, maxTotalBytes);
        allowedExtensions = normalize(allowedExtensions);
        allowedContentTypes = normalize(allowedContentTypes);
        if (allowedExtensions.isEmpty()) throw new IllegalArgumentException("RAG_ALLOWED_EXTENSIONS_REQUIRED");
        if (allowedContentTypes.isEmpty()) throw new IllegalArgumentException("RAG_ALLOWED_CONTENT_TYPES_REQUIRED");
    }

    public static RagIngestionJobSettings fromCsv(int maxFileCount,
                                                   long maxFileBytes,
                                                   long maxTotalBytes,
                                                   String allowedExtensions,
                                                   String allowedContentTypes) {
        return new RagIngestionJobSettings(
                maxFileCount,
                maxFileBytes,
                maxTotalBytes,
                csv(allowedExtensions),
                csv(allowedContentTypes));
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return normalize(new LinkedHashSet<>(Arrays.asList(value.split(","))));
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase());
            }
        }
        return Set.copyOf(normalized);
    }
}
