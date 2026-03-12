package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalSettingsAvailabilityBoundaryArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/";
    private static final String SERVICE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String SETTINGS = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalSettings.java";
    private static final String AVAILABILITY = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalAvailability.java";
    private static final String INITIALIZER = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalTableInitializer.java";
    private static final String READINESS = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalTableReadiness.java";
    private static final String CONFIGURATION = ROOT
            + "cn/lgs/orbisops/trigger/application/rag/OpsRagMultimodalConfiguration.java";

    @Test
    void serviceMustDelegateSettingsAvailabilityAndTableReadiness() throws IOException {
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(service.contains("RagMultimodalSettings settings")),
                () -> assertTrue(service.contains("RagMultimodalAvailability availability")),
                () -> assertTrue(service.contains("RagMultimodalTableReadiness tableReadiness")),
                () -> assertTrue(service.contains("RagMultimodalRuntimeComponents components")),
                () -> assertTrue(service.contains("this.settings = components.settings()")),
                () -> assertFalse(service.contains("IRagMultimodalRepository")),
                () -> assertTrue(service.contains("@Autowired")),
                () -> assertTrue(service.contains("settings.autoInit()")),
                () -> assertTrue(service.contains("tableReadiness.ensureReady()")),
                () -> assertTrue(service.contains("availability.evaluate()")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("SUPPORTED_PROVIDERS")),
                () -> assertFalse(service.contains("SUPPORTED_DIMENSIONS")),
                () -> assertFalse(service.contains("tableInitialized")),
                () -> assertFalse(service.contains("unavailableLogged")),
                () -> assertFalse(service.contains("AtomicBoolean")),
                () -> assertFalse(service.contains("multimodalRepository.ensureTable")),
                () -> assertFalse(service.contains("configuredDimension")),
                () -> assertTrue(service.contains("RagMultimodalEmbeddingProtocol embeddingProtocol")),
                () -> assertTrue(service.contains("RagMultimodalMediaPreparer mediaPreparer")),
                () -> assertTrue(service.contains("RagMultimodalIngestionProjector ingestionProjector")),
                () -> assertTrue(service.contains("RagMultimodalVectorStore vectorStore")),
                () -> assertTrue(service.contains("RagMultimodalRetrievalCoordinator retrievalCoordinator")),
                () -> assertFalse(service.contains("PDFRenderer")),
                () -> assertFalse(service.contains("ImageIO")),
                () -> assertFalse(service.contains("HttpClient")),
                () -> assertTrue(service.lines().count() <= 260));
    }

    @Test
    void typedSettingsMustOwnNormalizationAllowlistsDefaultsAndClampsWithoutFrameworkDependencies() throws IOException {
        String settings = read(SETTINGS);

        assertAll(
                () -> assertTrue(settings.contains("SUPPORTED_PROVIDERS")),
                () -> assertTrue(settings.contains("SUPPORTED_DIMENSIONS")),
                () -> assertTrue(settings.contains("SUPPORTED_IMAGE_MIME_TYPES")),
                () -> assertTrue(settings.contains("DEFAULT_DIMENSION")),
                () -> assertTrue(settings.contains("stripTrailingSlash")),
                () -> assertTrue(settings.contains("stripLeadingSlash")),
                () -> assertTrue(settings.contains("clamp(pdfRenderDpi, 72, 200)")),
                () -> assertTrue(settings.contains("clamp(defaultSearchTopK, 1, 30)")),
                () -> assertTrue(settings.contains("Math.max(5, timeoutSeconds)")),
                () -> assertTrue(settings.contains("Math.max(0, maxRetries)")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("PDFBox")),
                () -> assertFalse(settings.contains("PDFRenderer")),
                () -> assertFalse(settings.contains("HttpClient")),
                () -> assertFalse(settings.contains("ImageIO")),
                () -> assertFalse(settings.contains("RagFileResource")),
                () -> assertFalse(settings.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(settings.contains("IRagMultimodalRepository")));
    }

    @Test
    void availabilityAndReadinessMustOwnOneTimeWarningAndConcurrentInitializationRules() throws IOException {
        String availability = read(AVAILABILITY);
        String initializer = read(INITIALIZER);
        String readiness = read(READINESS);

        assertAll(
                () -> assertTrue(availability.contains("AtomicBoolean unavailableWarningDecided")),
                () -> assertTrue(availability.contains("compareAndSet(false, true)")),
                () -> assertTrue(availability.contains("settings.providerSupported()")),
                () -> assertTrue(availability.contains("repository.available()")),
                () -> assertFalse(availability.contains("Logger")),
                () -> assertFalse(availability.contains("PDFRenderer")),
                () -> assertTrue(initializer.contains("repository.ensureTable(settings.tableName(), settings.dimension())")),
                () -> assertTrue(readiness.contains("AtomicReference<CompletableFuture<Boolean>> inFlight")),
                () -> assertTrue(readiness.contains("inFlight.compareAndSet(null, created)")),
                () -> assertTrue(readiness.contains("initializer.initialize()")),
                () -> assertTrue(readiness.contains("ready.set(true)")),
                () -> assertFalse(readiness.contains("IRagMultimodalRepository")),
                () -> assertFalse(readiness.contains("ensureTable(")));
    }

    @Test
    void springConfigurationMustRemainTheOnlyOwnerOfMultimodalValueKeys() throws IOException {
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(configuration.contains("@Configuration")),
                () -> assertTrue(configuration.contains("@Bean")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.enabled:false}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.provider:}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.base-url:}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.api-key:}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.path:v1/multimodalembeddings}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.model:}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.table-name:orbisops_multimodal_vectors}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.dimension:2048}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.auto-init:true}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.index-text-documents:false}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.index-original-media:true}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.index-pdf-page-images:true}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.max-pdf-pages:3}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.pdf-render-dpi:144}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.max-image-bytes:20971520}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.max-text-chars:3000}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.search-top-k:8}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.timeout-seconds:30}")),
                () -> assertTrue(configuration.contains("${orbisops.rag.multimodal.max-retries:1}")),
                () -> assertTrue(configuration.contains("new RagMultimodalSettings(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
