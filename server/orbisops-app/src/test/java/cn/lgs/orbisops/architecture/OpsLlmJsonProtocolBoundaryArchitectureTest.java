package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmJsonProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesJsonExtractionParsingAndRepairProtocol() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String orchestrator = read(OPS + "OpsLlmJsonCallOrchestrator.java");
        String protocol = read(OPS + "OpsLlmJsonProtocol.java");

        assertAll(
                () -> assertTrue(client.contains("OpsLlmJsonCallOrchestrator jsonCallOrchestrator")),
                () -> assertTrue(client.contains("jsonCallOrchestrator.execute(")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.parse(")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.shouldRetryWithEagerSkillContext(")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.repairSystemPrompt()")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.repairUserPrompt(")),
                () -> assertFalse(client.contains("JSON.parseObject(")),
                () -> assertFalse(client.contains("private JsonParseResult parseJson(")),
                () -> assertFalse(client.contains("private String extractJson(")),
                () -> assertFalse(client.contains("private record JsonParseResult")),
                () -> assertFalse(client.contains("private String jsonRepairSystemPrompt(")),
                () -> assertFalse(client.contains("你是运维 Agent 的 JSON 自修复器")),
                () -> assertTrue(client.lines().count() <= 550),
                () -> assertTrue(protocol.contains("JSON.parseObject(")),
                () -> assertTrue(protocol.contains("ParseResult parse(")),
                () -> assertTrue(protocol.contains("shouldRetryWithEagerSkillContext(")),
                () -> assertTrue(protocol.contains("repairSystemPrompt(")),
                () -> assertTrue(protocol.contains("repairUserPrompt(")),
                () -> assertTrue(protocol.contains("private String extract(")),
                () -> assertTrue(protocol.contains("JSON 输出超过上限")),
                () -> assertTrue(protocol.contains("未返回 JSON 内容")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("org.springframework")),
                () -> assertTrue(protocol.lines().count() <= 140));
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
