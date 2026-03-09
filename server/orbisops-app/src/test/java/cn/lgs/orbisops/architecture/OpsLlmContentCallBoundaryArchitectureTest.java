package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmContentCallBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesObservableToolAwareModelContentCall() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String contentCall = read(OPS + "OpsLlmContentCallService.java");

        assertAll(
                () -> assertTrue(client.contains("OpsLlmContentCallService contentCallService")),
                () -> assertTrue(client.contains("contentCallService.call(")),
                () -> assertTrue(client.contains("new OpsLlmContentCallService.Input(")),
                () -> assertFalse(client.contains("OpsLlmTraceContext.current()")),
                () -> assertFalse(client.contains("System.nanoTime()")),
                () -> assertFalse(client.contains("skillContextService.skillTool(")),
                () -> assertFalse(client.contains("observabilityService.modelStarted(")),
                () -> assertFalse(client.contains("modelCallExecutor.execute(")),
                () -> assertFalse(client.contains("OpenAiChatOptions")),
                () -> assertFalse(client.contains(
                        "import org.springframework.ai.openai.api.ResponseFormat")),
                () -> assertFalse(client.contains("ToolCallback")),
                () -> assertTrue(client.lines().count() <= 220),
                () -> assertTrue(contentCall.contains("OpsLlmTraceContext.current()")),
                () -> assertTrue(contentCall.contains("observabilityService.modelStarted(")),
                () -> assertTrue(contentCall.contains("skillContextService.skillTool(")),
                () -> assertTrue(contentCall.contains("observabilityService.traceTool(")),
                () -> assertTrue(contentCall.contains("modelCallExecutor.execute(")),
                () -> assertTrue(contentCall.contains("new OpsLlmModelCallExecutor.Input(")),
                () -> assertTrue(contentCall.contains("OpenAiChatOptions responseOptions(")),
                () -> assertTrue(contentCall.contains("ResponseFormat.Type.JSON_OBJECT")),
                () -> assertTrue(contentCall.contains("observabilityService.modelCompleted(")),
                () -> assertTrue(contentCall.contains("observabilityService.modelFailed(")),
                () -> assertFalse(contentCall.contains("@Service")),
                () -> assertFalse(contentCall.contains("@Value")),
                () -> assertFalse(contentCall.contains("ApplicationContext")),
                () -> assertFalse(contentCall.contains("ObjectProvider")),
                () -> assertTrue(contentCall.lines().count() <= 140));
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
