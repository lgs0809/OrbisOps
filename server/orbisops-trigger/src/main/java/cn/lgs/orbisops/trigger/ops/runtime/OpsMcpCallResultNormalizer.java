package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps the complete wire envelope; chooses a typed value only after validating the advertised contract. */
final class OpsMcpCallResultNormalizer {
    private static final int MAX_BYTES = 1024 * 1024;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final JsonSchemaValidator schemas = new JacksonJsonSchemaValidatorSupplier().get();

    String normalize(McpSchema.CallToolResult result, Map<String, Object> outputSchema) {
        if (result == null) throw failure("EMPTY_RESPONSE");
        String raw = write(result);
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw failure("RESULT_TOO_LARGE");
        try { return validated(result, outputSchema); }
        catch (OpsMcpCallFailure invalid) { throw invalid.envelope(raw); }
    }

    private String validated(McpSchema.CallToolResult result, Map<String, Object> outputSchema) {
        if (Boolean.TRUE.equals(result.isError())) {
            throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.TOOL_ERROR, toolErrorSummary(result), true);
        }
        Map<String, Object> schema = outputSchema == null ? Map.of() : outputSchema;
        Object value = result.structuredContent();
        if (value == null) value = contentValue(result.content(), !schema.isEmpty());
        if (value == null || value instanceof String text && text.isBlank()) throw failure("EMPTY_CONTENT");
        if (!schema.isEmpty()) {
            rejectRemoteReferences(schema, true);
            try {
                if (!schemas.validate(schema, value).valid()) throw failure("OUTPUT_SCHEMA_MISMATCH");
            } catch (OpsMcpCallFailure invalid) { throw invalid; }
            catch (RuntimeException invalid) { throw failure("OUTPUT_SCHEMA_INVALID"); }
        }
        Map<String, Object> envelope = json.convertValue(result, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        // These top-level fields are locally generated, never taken from remote _meta.
        envelope.put("orbisopsResultVersion", 1);
        envelope.put("normalizedContent", value);
        envelope.put("outputSchemaHash", CanonicalObjectHasher.sha256(schema));
        return write(envelope);
    }

    private Object contentValue(List<McpSchema.Content> contents, boolean schemaRequired) {
        if (contents == null || contents.isEmpty()) throw failure("EMPTY_CONTENT");
        List<String> texts = new ArrayList<>();
        boolean other = false;
        for (McpSchema.Content content : contents) {
            if (content == null) throw failure("NULL_CONTENT_BLOCK");
            if (content instanceof McpSchema.TextContent text) texts.add(text.text() == null ? "" : text.text());
            else other = true;
        }
        if (schemaRequired) {
            // Legacy peers may accompany one complete JSON value with display
            // prose or image blocks. Select only an unambiguous JSON value;
            // never concatenate display text into evidence or choose between
            // conflicting structured values. The original envelope stays intact.
            List<Object> candidates = new ArrayList<>();
            boolean malformedJsonBlock = false;
            for (String text : texts) {
                String trimmed = text.trim();
                try { candidates.add(json.readValue(trimmed, Object.class)); }
                catch (JsonProcessingException invalid) {
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) malformedJsonBlock = true;
                }
            }
            if (candidates.isEmpty() && !texts.isEmpty()) {
                // Existing peers may split a single JSON document over text
                // blocks. Preserve that contract only when the entire joined
                // document parses strictly, with no trailing display text.
                try { return json.readValue(String.join("\n", texts), Object.class); }
                catch (JsonProcessingException invalid) {
                    if (malformedJsonBlock) throw failure("TEXT_JSON_INVALID");
                }
            }
            if (candidates.size() != 1) throw failure(candidates.isEmpty()
                    ? "STRUCTURED_CONTENT_REQUIRED" : "AMBIGUOUS_TEXT_JSON");
            if (malformedJsonBlock) throw failure("TEXT_JSON_INVALID");
            return candidates.get(0);
        }
        if (other) return contents;
        String joined = String.join("\n", texts);
        try { return json.readValue(joined, Object.class); }
        catch (JsonProcessingException invalid) {
            return joined; // MCP tools without outputSchema may legitimately return free text.
        }
    }

    private String toolErrorSummary(McpSchema.CallToolResult result) {
        String summary = result.content() == null ? "REMOTE_TOOL_REPORTED_ERROR" : result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance).map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n"));
        return summary.isBlank() ? "REMOTE_TOOL_REPORTED_ERROR" : summary.substring(0, Math.min(1200, summary.length()));
    }

    static void rejectRemoteReferences(Object value) {
        rejectRemoteReferences(value, false);
    }

    private static void rejectRemoteReferences(Object value, boolean dispatched) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                if (List.of("$ref", "$dynamicRef", "$recursiveRef").contains(key)
                        && !String.valueOf(item).startsWith("#")) throw new OpsMcpCallFailure(
                                OpsMcpCallFailure.Kind.CONTRACT_INVALID, "REMOTE_SCHEMA_REFERENCE_FORBIDDEN", dispatched);
                rejectRemoteReferences(item, dispatched);
            });
        } else if (value instanceof Iterable<?> list) list.forEach(item -> rejectRemoteReferences(item, dispatched));
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException invalid) { throw failure("ENVELOPE_SERIALIZATION_FAILED"); }
    }

    private OpsMcpCallFailure failure(String reason) {
        return new OpsMcpCallFailure(OpsMcpCallFailure.Kind.CONTRACT_INVALID, reason, true);
    }
}
