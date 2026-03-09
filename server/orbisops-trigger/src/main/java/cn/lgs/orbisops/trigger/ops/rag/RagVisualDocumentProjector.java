package cn.lgs.orbisops.trigger.ops.rag;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Parses visual model content and projects stable Spring AI documents. */
public final class RagVisualDocumentProjector {

    private final RagVisualAnalysisSettings settings;

    public RagVisualDocumentProjector(RagVisualAnalysisSettings settings) {
        if (settings == null) throw new IllegalArgumentException("RAG_VISUAL_SETTINGS_REQUIRED");
        this.settings = settings;
    }

    public Map<String, Object> sourceMetadata(
            Map<String, Object> baseMetadata,
            RagVisualMediaPreparer.PreparedImage image) {
        if ("pdf_page".equals(image.sourceType())) {
            return withMetadata(
                    baseMetadata,
                    "visual_source", image.sourceType(),
                    "visual_page_number", image.pageNumber(),
                    "page_number", image.pageNumber());
        }
        return withMetadata(
                baseMetadata,
                "visual_source", image.sourceType(),
                "visual_page_number", image.pageNumber());
    }

    public Document project(String content, Map<String, Object> metadata, int index) {
        try {
            return success(parseVisualContent(content), metadata, index);
        } catch (Exception e) {
            return rawText(content, metadata);
        }
    }

    public Document success(String content, Map<String, Object> metadata, int index) {
        return success(parseVisualContent(content), metadata, index);
    }

    public Document success(JSONObject parsed, Map<String, Object> metadata, int index) {
        Map<String, Object> documentMetadata = withMetadata(
                metadata,
                "chunk_strategy", "visual-structured-description",
                "visual_provider", RagVisualAnalysisSettings.PROVIDER_OPENAI,
                "visual_model", settings.model(),
                "visual_embedding_strategy", "visual_to_text_then_text_embedding",
                "embedding_input_modality", "text",
                "visual_parse_status", "success",
                "visual_confidence", parsed.getDoubleValue("confidence"));
        String text = formatVisualDocument(parsed);
        return document(documentMetadata, text, index);
    }

    public Document refused(String refusal, Map<String, Object> metadata) {
        return fallback(
                "Visual model refused to parse this image.",
                withMetadata(metadata, "visual_refusal", refusal),
                "refused");
    }

    public Document rawText(String content, Map<String, Object> metadata) {
        return fallback(
                "Visual extraction returned non-JSON content:\n" + abbreviate(content, 4000),
                withMetadata(metadata, "visual_parse_reason", "non_json_response"),
                "raw_text");
    }

    public Document tooLarge(
            RagVisualMediaPreparer.PreparedImage image,
            Map<String, Object> metadata) {
        return fallback(
                "Image skipped because it exceeds max-image-bytes.",
                withMetadata(
                        metadata,
                        "visual_parse_reason", image.rejectionReason(),
                        "visual_image_bytes", image.bytes().length),
                "skipped");
    }

    public Document failure(String message, Map<String, Object> metadata) {
        return fallback(message, metadata, "failed");
    }

    JSONObject parseVisualContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty visual response content");
        }
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized
                    .replaceFirst("^```[a-zA-Z]*\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            normalized = normalized.substring(objectStart, objectEnd + 1);
        }
        JSONObject parsed = JSONObject.parseObject(normalized);
        applyVisualDefaults(parsed);
        return parsed;
    }

    private void applyVisualDefaults(JSONObject parsed) {
        putDefault(parsed, "title", "Untitled visual document");
        putDefault(parsed, "summary", "");
        putDefault(parsed, "ocr_text", "");
        if (parsed.getJSONArray("tables") == null) {
            parsed.put("tables", new JSONArray());
        }
        if (parsed.getJSONArray("key_values") == null) {
            parsed.put("key_values", new JSONArray());
        }
        if (parsed.getJSONArray("operations_signals") == null) {
            parsed.put("operations_signals", new JSONArray());
        }
        if (parsed.getJSONArray("evidence_notes") == null) {
            parsed.put("evidence_notes", new JSONArray());
        }
        if (parsed.get("confidence") == null) {
            parsed.put("confidence", 0.5D);
        }
    }

    private void putDefault(JSONObject parsed, String key, String value) {
        String current = parsed.getString(key);
        if (current == null || current.isBlank()) {
            parsed.put(key, value);
        }
    }

    private String formatVisualDocument(JSONObject parsed) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Visual extraction: ")
                .append(parsed.getString("title"))
                .append("\n\n");
        builder.append("## Summary\n")
                .append(parsed.getString("summary"))
                .append("\n\n");
        builder.append("## OCR text\n")
                .append(parsed.getString("ocr_text"))
                .append("\n\n");
        JSONArray tables = parsed.getJSONArray("tables");
        if (tables != null && !tables.isEmpty()) {
            builder.append("## Tables\n");
            for (int i = 0; i < tables.size(); i++) {
                JSONObject table = tables.getJSONObject(i);
                builder.append("### ")
                        .append(table.getString("title"))
                        .append("\n")
                        .append(table.getString("markdown"))
                        .append("\n\n");
            }
        }
        JSONArray keyValues = parsed.getJSONArray("key_values");
        if (keyValues != null && !keyValues.isEmpty()) {
            builder.append("## Key values\n");
            for (int i = 0; i < keyValues.size(); i++) {
                JSONObject item = keyValues.getJSONObject(i);
                builder.append("- ")
                        .append(item.getString("key"))
                        .append(": ")
                        .append(item.getString("value"))
                        .append(" (confidence=")
                        .append(item.getDoubleValue("confidence"))
                        .append(")\n");
            }
            builder.append('\n');
        }
        appendList(builder, "Operations signals", parsed.getJSONArray("operations_signals"));
        appendList(builder, "Evidence notes", parsed.getJSONArray("evidence_notes"));
        builder.append("## Confidence\n")
                .append(parsed.getDoubleValue("confidence"));
        return builder.toString();
    }

    private void appendList(StringBuilder builder, String title, JSONArray values) {
        if (values == null || values.isEmpty()) return;
        builder.append("## ").append(title).append('\n');
        for (int i = 0; i < values.size(); i++) {
            builder.append("- ").append(values.getString(i)).append('\n');
        }
        builder.append('\n');
    }

    private Document fallback(String text, Map<String, Object> metadata, String status) {
        Map<String, Object> documentMetadata = withMetadata(
                metadata,
                "chunk_strategy", "visual-structured-description",
                "visual_provider", RagVisualAnalysisSettings.PROVIDER_OPENAI,
                "visual_model", settings.model(),
                "visual_embedding_strategy", "visual_to_text_then_text_embedding",
                "embedding_input_modality", "text",
                "visual_parse_status", status);
        return document(documentMetadata, text, 0);
    }

    private Document document(
            Map<String, Object> metadata,
            String text,
            int index) {
        return new Document(documentId(metadata, text, index), text, metadata);
    }

    private Map<String, Object> withMetadata(
            Map<String, Object> metadata,
            Object... pairs) {
        Map<String, Object> merged = new HashMap<>(metadata);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                merged.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return merged;
    }

    private String documentId(
            Map<String, Object> metadata,
            String text,
            int index) {
        String idSeed = metadata.getOrDefault("knowledge", "")
                + ":" + metadata.getOrDefault("source", "")
                + ":" + metadata.getOrDefault("visual_page_number", "")
                + ":" + index
                + ":" + text.hashCode();
        return UUID.nameUUIDFromBytes(
                idSeed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
