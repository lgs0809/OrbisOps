package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagDocumentParserSettingsTest {

    @Test
    void normalizesUnsafeBoundsAndPreservesLegacyZeroBudgets() {
        RagDocumentParserSettings normalized =
                new RagDocumentParserSettings(-1, 1_001);

        assertEquals(3_000, normalized.maxChunkChars());
        assertEquals(20, normalized.maxPdfImages());
        assertEquals(3_000, RagDocumentParserSettings.defaults().maxChunkChars());
        assertEquals(20, RagDocumentParserSettings.defaults().maxPdfImages());
        assertEquals(0,
                RagDocumentParserSettings.legacyConstructorDefaults().maxChunkChars());
        assertEquals(0,
                RagDocumentParserSettings.legacyConstructorDefaults().maxPdfImages());
    }
}
