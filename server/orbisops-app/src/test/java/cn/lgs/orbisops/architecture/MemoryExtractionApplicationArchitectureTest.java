package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExtractionApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryExtractionApplicationService.java";
    private static final String MODEL_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryModelExtractionPort.java";
    private static final String FAILURE_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryExtractionFailurePort.java";
    private static final String MODEL_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryModelExtractionAdapter.java";
    private static final String FAILURE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryExtractionFailureAdapter.java";
    private static final String EXTRACTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryExtractor.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsModelRuleMergeMaterializationFailureFallbackAndFinalLimit() throws IOException {
        String service = read(APPLICATION_SERVICE);
        String modelPort = read(MODEL_PORT);
        String failurePort = read(FAILURE_PORT);

        assertAll(
                () -> assertTrue(modelPort.contains("interface MemoryModelExtractionPort")),
                () -> assertTrue(modelPort.contains("List<MemoryExtractionDraft> extract(")),
                () -> assertTrue(failurePort.contains("interface MemoryExtractionFailurePort")),
                () -> assertTrue(service.contains("MemoryExtractionPolicy")),
                () -> assertTrue(service.contains("MemoryContentHashPolicy")),
                () -> assertTrue(service.contains("MemoryModelExtractionPort")),
                () -> assertTrue(service.contains("MemoryExtractionFailurePort")),
                () -> assertTrue(service.contains("modelExtractionPort.extract(")),
                () -> assertTrue(service.contains("extractionPolicy.ruleDrafts(message)")),
                () -> assertTrue(service.contains("extractionPolicy.materialize(")),
                () -> assertTrue(service.contains("extractionPolicy.distinctAndLimit(")),
                () -> assertTrue(service.contains("observeFailure(\"model-extraction\"")),
                () -> assertTrue(service.contains("catch (RuntimeException error)")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("ChatClient")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("OpsMemoryItem")));
    }

    @Test
    void triggerModelAdapterOwnsSpringAiPromptAvailabilityAndJsonCodecOnly() throws IOException {
        String modelAdapter = read(MODEL_ADAPTER);
        String failureAdapter = read(FAILURE_ADAPTER);

        assertAll(
                () -> assertTrue(modelAdapter.contains("implements MemoryModelExtractionPort")),
                () -> assertTrue(modelAdapter.contains("ModelAvailabilityPort")),
                () -> assertTrue(modelAdapter.contains("ChatClient.builder(chatModel)")),
                () -> assertTrue(modelAdapter.contains("modelResolver.resolve()")),
                () -> assertTrue(modelAdapter.contains("modelResolver.chatAvailable()")),
                () -> assertTrue(modelAdapter.contains("JSON.parseObject")),
                () -> assertTrue(modelAdapter.contains("JSON.toJSONString")),
                () -> assertTrue(modelAdapter.contains("List<MemoryExtractionDraft> decode(")),
                () -> assertFalse(modelAdapter.contains("MemoryExtractionPolicy")),
                () -> assertFalse(modelAdapter.contains("MemoryContentHashPolicy")),
                () -> assertFalse(modelAdapter.contains("MemoryItemCandidate")),
                () -> assertFalse(modelAdapter.contains("distinctAndLimit(")),
                () -> assertTrue(failureAdapter.contains("implements MemoryExtractionFailurePort")),
                () -> assertTrue(failureAdapter.contains("LLM 长期记忆抽取失败，降级使用规则抽取")),
                () -> assertFalse(failureAdapter.contains("MemoryExtractionPolicy")));
    }

    @Test
    void historicalExtractorOnlyMapsConfigurationToTypedApplicationCommand() throws IOException {
        String extractor = read(EXTRACTOR);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(extractor.contains("MemoryExtractionApplicationService")),
                () -> assertTrue(extractor.contains("new MemoryExtractionCommand(")),
                () -> assertTrue(extractor.contains("applicationService.extract(")),
                () -> assertTrue(extractor.contains("memoryMapper.snapshot(message)")),
                () -> assertTrue(extractor.contains("memoryMapper.candidateViews(")),
                () -> assertFalse(extractor.contains("MemoryExtractionPolicy")),
                () -> assertFalse(extractor.contains("MemoryContentHashPolicy")),
                () -> assertFalse(extractor.contains("MemoryModelExtractionPort")),
                () -> assertFalse(extractor.contains("ChatClient")),
                () -> assertFalse(extractor.contains("ChatModel")),
                () -> assertFalse(extractor.contains("ModelAvailabilityPort")),
                () -> assertFalse(extractor.contains("com.alibaba.fastjson")),
                () -> assertFalse(extractor.contains("@Slf4j")),
                () -> assertTrue(configuration.contains("memoryExtractionApplicationService(")),
                () -> assertTrue(configuration.contains("new MemoryExtractionPolicy()")),
                () -> assertTrue(configuration.contains("new MemoryContentHashPolicy()")),
                () -> assertTrue(configuration.contains("OpsMemoryModelExtractionAdapter")),
                () -> assertTrue(configuration.contains("OpsMemoryExtractionFailureAdapter")));
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
