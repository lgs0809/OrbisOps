package cn.lgs.orbisops.trigger.ops.rag;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualDocumentProjectorTest {

    private final RagVisualDocumentProjector projector =
            new RagVisualDocumentProjector(settings());

    @Test
    void fencedJsonMustParseAndFillStableDefaults() {
        JSONObject parsed = projector.parseVisualContent("""
                ```json
                {"title":"支付截图","summary":"支付回调失败","confidence":0.82}
                ```
                """);

        assertEquals("支付截图", parsed.getString("title"));
        assertEquals("支付回调失败", parsed.getString("summary"));
        assertEquals("", parsed.getString("ocr_text"));
        assertTrue(parsed.getJSONArray("tables").isEmpty());
        assertTrue(parsed.getJSONArray("key_values").isEmpty());
        assertTrue(parsed.getJSONArray("operations_signals").isEmpty());
        assertTrue(parsed.getJSONArray("evidence_notes").isEmpty());
        assertEquals(0.82D, parsed.getDoubleValue("confidence"), 0.001D);
    }

    @Test
    void successMustRenderMarkdownMetadataAndStableDocumentId() {
        String content = """
                {
                  "title":"慢查询图表",
                  "summary":"连接池耗尽",
                  "ocr_text":"timeout=30s",
                  "tables":[{"title":"指标","markdown":"|qps|error|","confidence":0.9}],
                  "key_values":[{"key":"error","value":"timeout","confidence":0.8}],
                  "operations_signals":["db saturation"],
                  "evidence_notes":["部分标签模糊"],
                  "confidence":0.86
                }
                """;
        Map<String, Object> metadata = Map.of(
                "knowledge", "operations",
                "source", "slow-query.png",
                "visual_page_number", 1);

        Document first = projector.success(content, metadata, 0);
        Document second = projector.success(content, metadata, 0);
        Document differentIndex = projector.success(content, metadata, 1);

        assertEquals(first.getId(), second.getId());
        assertNotEquals(first.getId(), differentIndex.getId());
        assertTrue(first.getText().contains("# Visual extraction: 慢查询图表"));
        assertTrue(first.getText().contains("## Tables"));
        assertTrue(first.getText().contains("- error: timeout (confidence=0.8)"));
        assertTrue(first.getText().contains("## Operations signals"));
        assertEquals("visual-structured-description",
                first.getMetadata().get("chunk_strategy"));
        assertEquals("openai", first.getMetadata().get("visual_provider"));
        assertEquals("visual-model", first.getMetadata().get("visual_model"));
        assertEquals("success", first.getMetadata().get("visual_parse_status"));
        assertEquals(0.86D,
                ((Number) first.getMetadata().get("visual_confidence")).doubleValue(),
                0.001D);
    }

    @Test
    void sourceMetadataMustDistinguishImageAndPdfPage() {
        RagVisualMediaPreparer.PreparedImage image = new RagVisualMediaPreparer.PreparedImage(
                new byte[]{1}, "image/png", "image", 1, 0, true, "");
        RagVisualMediaPreparer.PreparedImage page = new RagVisualMediaPreparer.PreparedImage(
                new byte[]{2}, "image/png", "pdf_page", 3, 2, true, "");

        Map<String, Object> imageMetadata = projector.sourceMetadata(Map.of("source", "a.png"), image);
        Map<String, Object> pageMetadata = projector.sourceMetadata(Map.of("source", "a.pdf"), page);

        assertEquals("image", imageMetadata.get("visual_source"));
        assertEquals(1, imageMetadata.get("visual_page_number"));
        assertFalse(imageMetadata.containsKey("page_number"));
        assertEquals("pdf_page", pageMetadata.get("visual_source"));
        assertEquals(3, pageMetadata.get("visual_page_number"));
        assertEquals(3, pageMetadata.get("page_number"));
    }

    @Test
    void fallbackVariantsMustPreserveStatusReasonAndModelMetadata() {
        Map<String, Object> metadata = Map.of(
                "knowledge", "operations",
                "source", "diagram.png",
                "visual_page_number", 1);
        RagVisualMediaPreparer.PreparedImage tooLarge =
                new RagVisualMediaPreparer.PreparedImage(
                        new byte[]{1, 2, 3}, "image/png", "image", 1, 0,
                        false, "image_too_large");

        Document refused = projector.refused("blocked", metadata);
        Document raw = projector.rawText("not-json", metadata);
        Document skipped = projector.tooLarge(tooLarge, metadata);
        Document failed = projector.failure("network failed", metadata);

        assertEquals("refused", refused.getMetadata().get("visual_parse_status"));
        assertEquals("blocked", refused.getMetadata().get("visual_refusal"));
        assertEquals("raw_text", raw.getMetadata().get("visual_parse_status"));
        assertEquals("non_json_response", raw.getMetadata().get("visual_parse_reason"));
        assertTrue(raw.getText().contains("not-json"));
        assertEquals("skipped", skipped.getMetadata().get("visual_parse_status"));
        assertEquals("image_too_large", skipped.getMetadata().get("visual_parse_reason"));
        assertEquals(3, skipped.getMetadata().get("visual_image_bytes"));
        assertEquals("failed", failed.getMetadata().get("visual_parse_status"));
        assertEquals("visual-model", failed.getMetadata().get("visual_model"));
    }

    private RagVisualAnalysisSettings settings() {
        return new RagVisualAnalysisSettings(
                true, false, "openai", "https://visual.example.com", "key",
                "v1/chat/completions", "visual-model", "low", 30, 1200,
                "max_completion_tokens", "json_schema", 1, 3,
                4_194_304L, 144);
    }
}
