package cn.lgs.orbisops.trigger.ops.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpsMcpCallResultNormalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String,Object> countSchema = Map.of("type", "object", "required", List.of("count"),
            "properties", Map.of("count", Map.of("type", "integer")));

    @Test void splitJsonDocumentRetainsExistingTextBlockContract() throws Exception {
        var result=json.readValue("{\"content\":[{\"type\":\"text\",\"text\":\"{\"},{\"type\":\"text\",\"text\":\"\\\"count\\\":3}\"}],\"isError\":false}",McpSchema.CallToolResult.class);
        var normalized=json.readTree(new OpsMcpCallResultNormalizer().normalize(result,countSchema));
        assertEquals(3,normalized.path("normalizedContent").path("count").asInt());assertEquals(2,normalized.path("content").size());
    }

    @Test void malformedJsonBesideValidJsonMustNotBeSilentlyDiscarded() throws Exception {
        var result=json.readValue("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"count\\\":3}\"},{\"type\":\"text\",\"text\":\"{broken\"}],\"isError\":false}",McpSchema.CallToolResult.class);
        var failure=assertThrows(OpsMcpCallFailure.class,()->new OpsMcpCallResultNormalizer().normalize(result,countSchema));
        assertTrue(failure.getMessage().contains("TEXT_JSON_INVALID"));assertFalse(OpsMcpFailureClassifier.retryable(failure));
    }

    @Test void oneJsonBlockWithDisplayProseOrImageRetainsTheWholeReceipt() throws Exception {
        for (String extra : List.of("{\"type\":\"text\",\"text\":\"Display only\"}",
                "{\"type\":\"image\",\"mimeType\":\"image/png\",\"data\":\"AQ==\"}")) {
            for (boolean jsonFirst : List.of(true, false)) {
                String evidence = "{\"type\":\"text\",\"text\":\"{\\\"count\\\":3}\"}";
                var result = json.readValue("{\"content\":["+(jsonFirst?evidence+","+extra:extra+","+evidence)+"],\"isError\":false}", McpSchema.CallToolResult.class);
                var normalized = json.readTree(new OpsMcpCallResultNormalizer().normalize(result, countSchema));
                assertEquals(3, normalized.path("normalizedContent").path("count").asInt());
                assertEquals(2, normalized.path("content").size());
            }
        }
    }

    @Test void conflictingJsonBlocksCannotBecomeAnAuthoritativeReceiptOrTriggerReplay() throws Exception {
        var result = json.readValue("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"count\\\":3}\"},{\"type\":\"text\",\"text\":\"{\\\"count\\\":4}\"}],\"isError\":false}", McpSchema.CallToolResult.class);
        var failure = assertThrows(OpsMcpCallFailure.class, () -> new OpsMcpCallResultNormalizer().normalize(result, countSchema));
        assertTrue(failure.getMessage().contains("AMBIGUOUS_TEXT_JSON"));
        assertTrue(failure.rawEnvelope().contains("count"));
        assertFalse(OpsMcpFailureClassifier.retryable(failure));
    }
    @Test void rejectedOutputSchemaAfterDispatchMustKeepUnknownWriteSemanticsAndRawReceipt() throws Exception {
        var schema = Map.<String, Object>of("$ref", "https://untrusted.invalid/output.json");
        var inputFailure = assertThrows(OpsMcpCallFailure.class, () -> OpsMcpCallResultNormalizer.rejectRemoteReferences(schema));
        assertFalse(inputFailure.dispatched());
        var result = new ObjectMapper().readValue("{\"content\":[],\"isError\":false,\"structuredContent\":{\"count\":1}}", McpSchema.CallToolResult.class);
        var outputFailure = assertThrows(OpsMcpCallFailure.class, () -> new OpsMcpCallResultNormalizer().normalize(result, schema));
        assertTrue(outputFailure.dispatched());
        assertEquals(OpsMcpCallFailure.Kind.CONTRACT_INVALID, outputFailure.kind());
        assertFalse(OpsMcpFailureClassifier.retryable(outputFailure));
        assertTrue(outputFailure.rawEnvelope().contains("structuredContent"));
    }
}
