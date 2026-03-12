package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualSettingsAvailabilityBoundaryArchitectureTest {

    private static final String RAG =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/";
    private static final String CONFIGURATION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/rag/"
                    + "OpsRagVisualAnalysisConfiguration.java";

    @Test
    void visualConfigurationAndAvailabilityMustHaveTypedOwners() throws IOException {
        String analyzer = read(RAG + "RagVisualDocumentAnalyzer.java");
        String settings = read(RAG + "RagVisualAnalysisSettings.java");
        String availability = read(RAG + "RagVisualAnalysisAvailability.java");
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(analyzer.contains("RagVisualAnalysisAvailability availability")),
                () -> assertTrue(analyzer.contains("return availability.shouldAnalyze(metadata)")),
                () -> assertTrue(analyzer.contains("RagVisualAnalysisSettings settings")),
                () -> assertFalse(analyzer.contains("@Value")),
                () -> assertFalse(analyzer.contains("orbisops.rag.parse.visual.")),
                () -> assertFalse(analyzer.contains("isRerankAvailable(")),
                () -> assertFalse(analyzer.contains("high_value_candidate")),
                () -> assertTrue(settings.contains("PROVIDER_OPENAI = \"openai\"")),
                () -> assertTrue(settings.contains("providerSupported()")),
                () -> assertTrue(settings.contains("endpoint()")),
                () -> assertTrue(settings.contains("Math.max(5, timeoutSeconds)")),
                () -> assertTrue(settings.contains(
                        "Math.max(300, Math.min(4000, maxCompletionTokens))")),
                () -> assertTrue(settings.contains("Math.max(0, maxRetries)")),
                () -> assertTrue(settings.contains("Math.max(1, maxImagesPerDocument)")),
                () -> assertTrue(settings.contains(
                        "Math.max(72, Math.min(200, pdfRenderDpi))")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("HttpClient")),
                () -> assertFalse(settings.contains("PDFRenderer")),
                () -> assertTrue(availability.contains("settings.enabled()")),
                () -> assertTrue(availability.contains("settings.providerSupported()")),
                () -> assertTrue(availability.contains("isRerankAvailable(settings.apiKey())")),
                () -> assertTrue(availability.contains("high_value_candidate")),
                () -> assertFalse(availability.contains("org.springframework")),
                () -> assertFalse(availability.contains("HttpClient")),
                () -> assertFalse(availability.contains("PDFRenderer")),
                () -> assertFalse(availability.contains("JSONObject")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.enabled:false}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.high-value-only:true}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.provider:openai}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.base-url:${spring.ai.openai.base-url:}}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.api-key:${spring.ai.openai.api-key:}}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.path:v1/chat/completions}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.model:}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.detail:low}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.timeout-seconds:30}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.max-completion-tokens:1200}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.token-limit-field:max_completion_tokens}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.response-format:json_schema}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.max-retries:1}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.max-images-per-document:3}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.max-image-bytes:4194304}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.rag.parse.visual.pdf-render-dpi:144}")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
