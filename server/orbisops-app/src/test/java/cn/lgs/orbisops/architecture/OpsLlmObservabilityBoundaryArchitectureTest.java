package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmObservabilityBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesModelAndToolRuntimeEventsToObservabilityBoundary() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String contentCall = read(OPS + "OpsLlmContentCallService.java");
        String orchestrator = read(OPS + "OpsLlmJsonCallOrchestrator.java");
        String observability = read(OPS + "OpsLlmObservabilityService.java");

        assertAll(
                () -> assertTrue(client.contains("new OpsLlmObservabilityService()")),
                () -> assertFalse(client.contains("observabilityService.modelStarted(")),
                () -> assertFalse(client.contains("observabilityService.modelCompleted(")),
                () -> assertFalse(client.contains("observabilityService.modelFailed(")),
                () -> assertFalse(client.contains("observabilityService.modelJsonInvalid(")),
                () -> assertFalse(client.contains("observabilityService.traceTool(")),
                () -> assertTrue(contentCall.contains("observabilityService.modelStarted(")),
                () -> assertTrue(contentCall.contains("observabilityService.modelCompleted(")),
                () -> assertTrue(contentCall.contains("observabilityService.modelFailed(")),
                () -> assertTrue(contentCall.contains("observabilityService.traceTool(")),
                () -> assertTrue(orchestrator.contains("observabilityService.modelJsonInvalid(")),
                () -> assertFalse(client.contains("OpsRuntimeEvent.builder(")),
                () -> assertFalse(client.contains("private ToolCallback traceTool(")),
                () -> assertFalse(client.contains("private ToolCallback wrapToolCallback(")),
                () -> assertFalse(client.contains("recordModelTraceEvent(")),
                () -> assertFalse(client.contains("recordLlmTraceEvent(")),
                () -> assertFalse(client.contains("recordToolTraceEvent(")),
                () -> assertFalse(client.contains("modelTracePayload(")),
                () -> assertFalse(client.contains("llmTracePayload(")),
                () -> assertFalse(client.contains("toolTracePayload(")),
                () -> assertFalse(client.contains("private String toolName(")),
                () -> assertFalse(client.contains("private Map<String, Object> toolArguments(")),
                () -> assertFalse(client.contains("private String abbreviate(")),
                () -> assertFalse(client.contains("ToolContext")),
                () -> assertTrue(client.lines().count() <= 350),
                () -> assertTrue(observability.contains("OpsRuntimeEvent.builder(")),
                () -> assertTrue(observability.contains("void modelStarted(")),
                () -> assertTrue(observability.contains("void modelCompleted(")),
                () -> assertTrue(observability.contains("void modelFailed(")),
                () -> assertTrue(observability.contains("void modelJsonInvalid(")),
                () -> assertTrue(observability.contains("ToolCallback traceTool(")),
                () -> assertTrue(observability.contains("OpsToolSchemaNormalizer.normalize(")),
                () -> assertTrue(observability.contains("delegate.call(toolInput)")),
                () -> assertTrue(observability.contains("delegate.call(toolInput, toolContext)")),
                () -> assertTrue(observability.contains("MODEL_CALL_STARTED")),
                () -> assertTrue(observability.contains("MODEL_CALL_FINISHED")),
                () -> assertTrue(observability.contains("MODEL_JSON_INVALID")),
                () -> assertTrue(observability.contains("TOOL_CALL_FINISHED")),
                () -> assertFalse(observability.contains("parentTrace.child(")),
                () -> assertFalse(observability.contains("@Service")),
                () -> assertFalse(observability.contains("@Value")),
                () -> assertFalse(observability.contains("org.springframework.beans")),
                () -> assertFalse(observability.contains("org.springframework.context")),
                () -> assertFalse(observability.contains("org.springframework.util")),
                () -> assertTrue(observability.lines().count() <= 350));
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
