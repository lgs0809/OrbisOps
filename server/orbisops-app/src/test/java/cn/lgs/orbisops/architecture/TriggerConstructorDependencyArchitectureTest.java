package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerConstructorDependencyArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/";
    private static final String TEST = "orbisops-app/src/test/java/";

    @Test
    void mcpConfigFacadeMustUseTypedCatalogUseCase() throws IOException {
        String service = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/config/AiClientToolMcpApplicationService.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/config/McpClientCatalogConfiguration.java");
        String test = read(TEST
                + "cn/lgs/orbisops/trigger/application/config/AiClientToolMcpApplicationServiceTest.java");

        assertAll(
                () -> assertTrue(service.contains("private final McpClientCatalogUseCase catalogUseCase")),
                () -> assertTrue(configuration.contains("ObjectProvider<OpsMcpToolProvider> toolProvider")),
                () -> assertTrue(configuration.contains("toolProvider.getIfAvailable()")),
                () -> assertFalse(service.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(service.contains("OpsMcpToolProvider")),
                () -> assertFalse(service.contains("OpsConfigAuditService")),
                () -> assertFalse(service.contains("ObjectProvider<")),
                () -> assertFalse(service.contains("@Resource")),
                () -> assertFalse(service.contains("@Autowired")),
                () -> assertFalse(test.contains("ReflectionTestUtils")),
                () -> assertFalse(test.contains("setField(service")));
    }

    @Test
    void ragQualityMustUseTypedSettingsAndDedicatedCompositionRoot() throws IOException {
        String service = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/RagQualityEvalService.java");
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/OpsRagQualityRetrievalAdapter.java");
        String assembly = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/OpsRagQualityEvalManagementAssembly.java");
        String settings = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/RagQualityEvalSettings.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/RagQualityEvalConfiguration.java");

        assertAll(
                () -> assertTrue(adapter.contains("private final RagMultimodalEmbeddingService multimodalEmbeddingService")),
                () -> assertTrue(adapter.contains("private final RagQualityEvalSettings settings")),
                () -> assertTrue(adapter.contains("settings.rerankProvider()")),
                () -> assertTrue(configuration.contains("ObjectProvider<RagMultimodalEmbeddingService> multimodalEmbeddingProvider")),
                () -> assertTrue(configuration.contains("opsRagQualityEvalManagementAssembly")),
                () -> assertTrue(configuration.contains("OpsRagQualityEvalManagementAssembly.create")),
                () -> assertTrue(assembly.contains("new OpsRagQualityRetrievalAdapter")),
                () -> assertTrue(assembly.contains("new OpsRagQualityCaseCatalogAdapter")),
                () -> assertTrue(assembly.contains("new OpsRagQualityRunPersistenceAdapter")),
                () -> assertTrue(assembly.contains("new RagQualityProbeUseCase")),
                () -> assertTrue(assembly.contains("new RagQualityRunUseCase")),
                () -> assertTrue(service.contains("RagQualityEvalService(OpsRagQualityEvalManagementAssembly assembly)")),
                () -> assertTrue(service.contains("settings.rerankEnabled()")),
                () -> assertTrue(service.contains("private final RagQualityProbeUseCase")),
                () -> assertFalse(service.contains("ObjectProvider<")),
                () -> assertFalse(service.contains("IRagEvalRepository")),
                () -> assertFalse(service.contains("IRagKnowledgeRepository")),
                () -> assertFalse(service.contains("new OpsRagQuality")),
                () -> assertFalse(service.contains("new RagQualityProbeUseCase")),
                () -> assertFalse(service.contains("new RagQualityRunUseCase")),
                () -> assertFalse(service.contains("new RagAnswerAdvisor")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("@Autowired")),
                () -> assertFalse(service.contains("ragEvalRerankEnabled")),
                () -> assertTrue(settings.contains("public record RagQualityEvalSettings(")),
                () -> assertTrue(configuration.contains("orbisops.rag.eval.rerank-enabled")),
                () -> assertTrue(configuration.contains("orbisops.rag.eval.rerank-model")));
    }

    @Test
    void triggerProductionCodeMustNotUseRequiredFalseAutowired() throws IOException {
        Path root = projectRoot().resolve("orbisops-trigger/src/main/java");
        try (var paths = Files.walk(root)) {
            String source = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::readUnchecked)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertFalse(source.contains("@Autowired(required = false)"));
        }
    }

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
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
