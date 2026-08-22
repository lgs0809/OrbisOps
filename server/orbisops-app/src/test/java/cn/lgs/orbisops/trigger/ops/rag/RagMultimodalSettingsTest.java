package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalSettingsTest {

    @Test
    void shouldNormalizeSupportedProviderAndEndpoint() {
        RagMultimodalSettings settings = settings(
                true, " QWEN-VL ", "http://127.0.0.1:8110///", "secret", "///v1/embed", 1024,
                3, 144, 20_971_520L, 3000, 8, 30, 1);

        assertEquals("qwen-vl", settings.provider());
        assertTrue(settings.providerSupported());
        assertEquals("http://127.0.0.1:8110/v1/embed", settings.endpoint());
        assertEquals(1024, settings.dimension());
    }

    @Test
    void shouldRejectUnsupportedProviderAndDefaultUnsupportedDimension() {
        RagMultimodalSettings settings = settings(
                true, "unknown-provider", "http://localhost", "secret", "embed", 999,
                3, 144, 20_971_520L, 3000, 8, 30, 1);

        assertFalse(settings.providerSupported());
        assertEquals(RagMultimodalSettings.DEFAULT_DIMENSION, settings.dimension());
    }

    @Test
    void shouldClampOperationalLimitsDeterministically() {
        RagMultimodalSettings lower = settings(
                true, "voyage", "http://localhost", "secret", "embed", 2048,
                0, 20, 10L, 12, 0, 1, -4);
        RagMultimodalSettings upper = settings(
                true, "voyage", "http://localhost", "secret", "embed", 2048,
                4, 999, 10L, 12, 99, 60, 3);

        assertEquals(1, lower.maxPdfPages());
        assertEquals(72, lower.pdfRenderDpi());
        assertEquals(1, lower.defaultSearchTopK());
        assertEquals(1, lower.resolveSearchTopK(0));
        assertEquals(30, lower.resolveSearchTopK(99));
        assertEquals(5, lower.timeoutSeconds());
        assertEquals(0, lower.maxRetries());

        assertEquals(200, upper.pdfRenderDpi());
        assertEquals(30, upper.defaultSearchTopK());
        assertEquals(30, upper.resolveSearchTopK(-1));
        assertEquals(60, upper.timeoutSeconds());
        assertEquals(3, upper.maxRetries());
    }

    @Test
    void defaultsMustRemainProviderNeutralUntilExplicitlyConfigured() {
        RagMultimodalSettings settings = RagMultimodalSettings.defaults();

        assertFalse(settings.enabled());
        assertEquals("", settings.provider());
        assertFalse(settings.providerSupported());
        assertEquals("/v1/multimodalembeddings", settings.endpoint());
        assertEquals("", settings.apiKey());
        assertEquals("", settings.model());
        assertEquals("orbisops_multimodal_vectors", settings.tableName());
        assertEquals(2048, settings.dimension());
        assertTrue(settings.autoInit());
        assertFalse(settings.indexTextDocuments());
        assertTrue(settings.indexOriginalMedia());
        assertTrue(settings.indexPdfPageImages());
        assertEquals(3, settings.maxPdfPages());
        assertEquals(144, settings.pdfRenderDpi());
        assertEquals(20_971_520L, settings.maxImageBytes());
        assertEquals(3000, settings.maxTextChars());
        assertEquals(8, settings.defaultSearchTopK());
        assertEquals(30, settings.timeoutSeconds());
        assertEquals(1, settings.maxRetries());
        assertTrue(settings.supportsImageMimeType("image/jpeg"));
        assertFalse(settings.supportsImageMimeType("image/tiff"));
    }

    private RagMultimodalSettings settings(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String path,
            int dimension,
            int maxPdfPages,
            int pdfRenderDpi,
            long maxImageBytes,
            int maxTextChars,
            int defaultSearchTopK,
            int timeoutSeconds,
            int maxRetries) {
        return new RagMultimodalSettings(
                enabled,
                provider,
                baseUrl,
                apiKey,
                path,
                "model",
                "table_name",
                dimension,
                true,
                false,
                true,
                true,
                maxPdfPages,
                pdfRenderDpi,
                maxImageBytes,
                maxTextChars,
                defaultSearchTopK,
                timeoutSeconds,
                maxRetries);
    }
}
