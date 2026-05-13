package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualDocumentAnalyzerTest {

    @Test
    void noArgCompatibilityConstructorMustRemainDisabledByDefault() {
        RagVisualDocumentAnalyzer analyzer = new RagVisualDocumentAnalyzer();

        assertFalse(analyzer.shouldAnalyze(Map.of("high_value_candidate", true)));
    }

    @Test
    void imageSuccessMustCoordinateMediaProtocolAndProjection() {
        AtomicInteger calls = new AtomicInteger();
        RagVisualDocumentAnalyzer analyzer = analyzer(
                settings(1024),
                request -> {
                    calls.incrementAndGet();
                    return new RagVisualAnalysisProtocol.TransportResponse(
                            200,
                            responseBody(content("diagram", "visible error"), null));
                });

        List<Document> documents = analyzer.analyzeImage(
                resource("diagram.png", "image/png", new byte[]{1, 2, 3}),
                Map.of("knowledge", "ops", "source", "diagram.png"));

        assertEquals(1, documents.size());
        assertEquals(1, calls.get());
        Document document = documents.get(0);
        assertTrue(document.getText().contains("# Visual extraction: diagram"));
        assertEquals("success", document.getMetadata().get("visual_parse_status"));
        assertEquals("image", document.getMetadata().get("visual_source"));
        assertEquals(1, document.getMetadata().get("visual_page_number"));
    }

    @Test
    void oversizedImageMustSkipProtocolAndReturnSkippedDocument() {
        AtomicInteger calls = new AtomicInteger();
        RagVisualDocumentAnalyzer analyzer = analyzer(
                settings(2),
                request -> {
                    calls.incrementAndGet();
                    return new RagVisualAnalysisProtocol.TransportResponse(
                            200,
                            responseBody(content("unused", "unused"), null));
                });

        Document document = analyzer.analyzeImage(
                resource("large.png", "image/png", new byte[]{1, 2, 3}),
                Map.of("knowledge", "ops", "source", "large.png"))
                .get(0);

        assertEquals(0, calls.get());
        assertEquals("skipped", document.getMetadata().get("visual_parse_status"));
        assertEquals("image_too_large", document.getMetadata().get("visual_parse_reason"));
        assertEquals(3, document.getMetadata().get("visual_image_bytes"));
    }

    @Test
    void refusalAndNonJsonContentMustUseStableFallbacks() {
        AtomicInteger calls = new AtomicInteger();
        RagVisualDocumentAnalyzer analyzer = analyzer(
                settings(1024),
                request -> calls.getAndIncrement() == 0
                        ? new RagVisualAnalysisProtocol.TransportResponse(
                                200,
                                responseBody(null, "blocked"))
                        : new RagVisualAnalysisProtocol.TransportResponse(
                                200,
                                responseBody("plain response", null)));

        Document refused = analyzer.analyzeImage(
                resource("one.png", "image/png", new byte[]{1}),
                Map.of("source", "one.png"))
                .get(0);
        Document raw = analyzer.analyzeImage(
                resource("two.png", "image/png", new byte[]{2}),
                Map.of("source", "two.png"))
                .get(0);

        assertEquals("refused", refused.getMetadata().get("visual_parse_status"));
        assertEquals("blocked", refused.getMetadata().get("visual_refusal"));
        assertEquals("raw_text", raw.getMetadata().get("visual_parse_status"));
        assertEquals("non_json_response", raw.getMetadata().get("visual_parse_reason"));
        assertTrue(raw.getText().contains("plain response"));
    }

    @Test
    void protocolFailureMustReturnFailedImageFallback() {
        RagVisualDocumentAnalyzer analyzer = analyzer(
                settings(1024),
                request -> new RagVisualAnalysisProtocol.TransportResponse(500, "down"));

        Document document = analyzer.analyzeImage(
                resource("failed.png", "image/png", new byte[]{1}),
                Map.of("source", "failed.png"))
                .get(0);

        assertEquals("failed", document.getMetadata().get("visual_parse_status"));
        assertTrue(document.getText().contains("Image visual extraction failed:"));
        assertTrue(document.getText().contains("Visual parse HTTP 500 down"));
    }

    @Test
    void invalidPdfMustReturnFailedPdfFallback() {
        RagVisualDocumentAnalyzer analyzer = analyzer(
                settings(1024),
                request -> new RagVisualAnalysisProtocol.TransportResponse(
                        200,
                        responseBody(content("unused", "unused"), null)));

        List<Document> documents = analyzer.analyzePdf(
                resource("broken.pdf", "application/pdf", new byte[]{1, 2, 3}),
                Map.of("source", "broken.pdf"));

        assertEquals(1, documents.size());
        assertEquals("failed", documents.get(0).getMetadata().get("visual_parse_status"));
        assertTrue(documents.get(0).getText().contains("PDF visual extraction failed:"));
    }

    private RagVisualDocumentAnalyzer analyzer(
            RagVisualAnalysisSettings settings,
            RagVisualAnalysisProtocol.HttpTransport transport) {
        RagVisualAnalysisAvailability availability =
                new RagVisualAnalysisAvailability(settings, null);
        RagVisualMediaPreparer mediaPreparer = new RagVisualMediaPreparer(settings);
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(
                settings,
                transport,
                attempt -> { });
        RagVisualDocumentProjector projector =
                new RagVisualDocumentProjector(settings);
        return new RagVisualDocumentAnalyzer(
                availability,
                mediaPreparer,
                protocol,
                projector);
    }

    private RagVisualAnalysisSettings settings(long maxImageBytes) {
        return new RagVisualAnalysisSettings(
                true,
                false,
                "openai",
                "https://visual.example.com",
                "key",
                "v1/chat/completions",
                "visual-model",
                "low",
                30,
                1200,
                "max_completion_tokens",
                "json_schema",
                0,
                3,
                maxImageBytes,
                144);
    }

    private String content(String title, String summary) {
        JSONObject content = new JSONObject(true);
        content.put("title", title);
        content.put("summary", summary);
        content.put("ocr_text", "error=timeout");
        content.put("tables", new JSONArray());
        content.put("key_values", new JSONArray());
        content.put("operations_signals", new JSONArray());
        content.put("evidence_notes", new JSONArray());
        content.put("confidence", 0.9D);
        return content.toJSONString();
    }

    private String responseBody(String content, String refusal) {
        JSONObject message = new JSONObject(true);
        if (refusal != null) {
            message.put("refusal", refusal);
        } else {
            message.put("content", content);
        }
        JSONObject choice = new JSONObject(true);
        choice.put("message", message);
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject response = new JSONObject(true);
        response.put("choices", choices);
        return response.toJSONString();
    }

    private RagFileResource resource(
            String fileName,
            String contentType,
            byte[] bytes) {
        return new RagFileResource() {
            @Override
            public String name() {
                return "file";
            }

            @Override
            public String originalFilename() {
                return fileName;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public long size() {
                return bytes.length;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }
}
