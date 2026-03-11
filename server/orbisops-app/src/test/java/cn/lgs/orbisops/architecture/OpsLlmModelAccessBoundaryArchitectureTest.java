package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmModelAccessBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesModelDiscoveryAvailabilityAndBaseStatus() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String access = read(OPS + "OpsLlmModelAccessService.java");

        assertAll(
                () -> assertTrue(client.contains("OpsLlmModelAccessService modelAccessService")),
                () -> assertTrue(client.contains("modelAccessService.available(settings.enabled())")),
                () -> assertTrue(client.contains("modelAccessService.status(settings.enabled())")),
                () -> assertTrue(client.contains("modelAccessService.resolveAvailable(settings.enabled())")),
                () -> assertFalse(client.contains("private final ApplicationContext")),
                () -> assertFalse(client.contains("private final ObjectProvider<ChatModel>")),
                () -> assertFalse(client.contains("private final ModelAvailabilityPort")),
                () -> assertFalse(client.contains("private ChatModel resolveChatModel(")),
                () -> assertFalse(client.contains("getBean(\"openAiChatModel\"")),
                () -> assertFalse(client.contains("isChatAvailable()")),
                () -> assertTrue(client.lines().count() <= 330),
                () -> assertTrue(access.contains("ChatModel resolveAvailable(boolean enabled)")),
                () -> assertTrue(access.contains("boolean available(boolean enabled)")),
                () -> assertTrue(access.contains("Map<String, Object> status(boolean enabled)")),
                () -> assertTrue(access.contains("getBean(\"openAiChatModel\", ChatModel.class)")),
                () -> assertTrue(access.contains("chatModelProvider.getIfUnique()")),
                () -> assertTrue(access.contains("aiModelAvailability.isChatAvailable()")),
                () -> assertFalse(access.contains("@Service")),
                () -> assertFalse(access.contains("@Value")),
                () -> assertTrue(access.lines().count() <= 80));
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
