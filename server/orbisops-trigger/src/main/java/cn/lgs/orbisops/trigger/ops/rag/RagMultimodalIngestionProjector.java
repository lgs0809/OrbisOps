package cn.lgs.orbisops.trigger.ops.rag;

import org.springframework.ai.document.Document;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Projects text and prepared media into stable multimodal ingestion values. */
public final class RagMultimodalIngestionProjector {

    private static final String EMPTY_MEDIA_DOCUMENT_ID = "multimodal-empty";
    private static final String EMPTY_MEDIA_DOCUMENT_TEXT = "Multimodal media document.";
    private static final String MEDIA_FALLBACK_TEXT =
            "Original media was indexed with native multimodal embedding. Enable visual parsing to add text evidence.";

    private final RagMultimodalSettings settings;

    public RagMultimodalIngestionProjector(RagMultimodalSettings settings) {
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        this.settings = settings;
    }

    public Optional<TextProjection> projectText(Document document) {
        if (document == null || !hasText(document.getText())) {
            return Optional.empty();
        }
        String content = abbreviate(document.getText(), settings.maxTextChars());
        Map<String, Object> metadata = multimodalMetadata(
                document.getMetadata(),
                "multimodal_media_type", "text",
                "embedding_input_modality", "text",
                "multimodal_embedding_strategy", "text_in_shared_multimodal_space");
        return Optional.of(new TextProjection(
                document.getId() + ":mm:text",
                content,
                metadata,
                source(metadata)));
    }

    public MediaProjection projectMedia(
            List<Document> documents,
            RagMultimodalMediaPreparer.PreparedMedia media) {
        if (media == null) throw new IllegalArgumentException("RAG_MULTIMODAL_PREPARED_MEDIA_REQUIRED");
        byte[] imageBytes = media.bytes();
        Document representative = representativeDocument(documents, media.pageNumber());
        String imageHash = sha256(imageBytes);
        String content = mediaContent(representative, media.mediaType(), media.pageNumber());
        Map<String, Object> metadata = multimodalMetadata(
                representative.getMetadata(),
                "multimodal_media_type", media.mediaType(),
                "visual_source", media.mediaType(),
                "visual_page_number", media.pageNumber(),
                "embedding_input_modality", "image",
                "multimodal_embedding_strategy", "native_image_embedding",
                "multimodal_image_mime_type", media.mimeType(),
                "multimodal_image_sha256", imageHash);
        return new MediaProjection(
                representative.getId() + ":mm:" + media.mediaType() + ":" + media.pageNumber() + ":" + imageHash,
                content,
                metadata,
                imageBytes,
                media.mimeType(),
                media.pageNumber(),
                media.mediaType(),
                source(metadata));
    }

    public int pageNumber(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private Document representativeDocument(List<Document> documents, int pageNumber) {
        if (documents == null || documents.isEmpty()) {
            return new Document(EMPTY_MEDIA_DOCUMENT_ID, EMPTY_MEDIA_DOCUMENT_TEXT, new HashMap<>());
        }
        return documents.stream()
                .filter(document -> pageNumber == pageNumber(document.getMetadata().get("page_number"))
                        || pageNumber == pageNumber(document.getMetadata().get("visual_page_number")))
                .findFirst()
                .orElse(documents.get(0));
    }

    private String mediaContent(Document document, String mediaType, int pageNumber) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Multimodal media embedding\n\n");
        builder.append("Source: ").append(document.getMetadata().getOrDefault("source", "unknown")).append('\n');
        builder.append("Media type: ").append(mediaType).append('\n');
        builder.append("Page: ").append(pageNumber).append("\n\n");
        if (hasText(document.getText())) {
            builder.append(abbreviate(document.getText(), settings.maxTextChars()));
        } else {
            builder.append(MEDIA_FALLBACK_TEXT);
        }
        return builder.toString();
    }

    private Map<String, Object> multimodalMetadata(Map<String, Object> source, Object... pairs) {
        Map<String, Object> metadata = source == null ? new HashMap<>() : new HashMap<>(source);
        metadata.put("multimodal_embedding_provider", settings.provider());
        metadata.put("multimodal_embedding_model", settings.model());
        metadata.put("multimodal_embedding_dimension", settings.dimension());
        metadata.put("multimodal_table", settings.tableName());
        metadata.put("retrieval_source", "multimodal");
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (pairs[index] != null && pairs[index + 1] != null) {
                metadata.put(String.valueOf(pairs[index]), pairs[index + 1]);
            }
        }
        return Collections.unmodifiableMap(metadata);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            return String.valueOf(Arrays.hashCode(bytes));
        }
    }

    private static String source(Map<String, Object> metadata) {
        return String.valueOf(metadata.getOrDefault("source", "unknown"));
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record TextProjection(
            String id,
            String content,
            Map<String, Object> metadata,
            String source) {

        public TextProjection {
            metadata = immutableMetadata(metadata);
            source = source == null ? "unknown" : source;
        }
    }

    public record MediaProjection(
            String id,
            String content,
            Map<String, Object> metadata,
            byte[] bytes,
            String mimeType,
            int pageNumber,
            String mediaType,
            String source) {

        public MediaProjection {
            metadata = immutableMetadata(metadata);
            bytes = bytes == null ? new byte[0] : bytes.clone();
            mimeType = mimeType == null ? "" : mimeType;
            mediaType = mediaType == null ? "" : mediaType;
            source = source == null ? "unknown" : source;
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private static Map<String, Object> immutableMetadata(Map<String, Object> metadata) {
        Map<String, Object> copy = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        return Collections.unmodifiableMap(copy);
    }
}
