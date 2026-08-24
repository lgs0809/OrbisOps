package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmSkillContextBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesSkillContextToolBindingAndNameNormalization() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String orchestrator = read(OPS + "OpsLlmJsonCallOrchestrator.java");
        String contentCall = read(OPS + "OpsLlmContentCallService.java");
        String service = read(OPS + "OpsLlmSkillContextService.java");

        assertAll(
                () -> assertTrue(client.contains("new OpsLlmSkillContextService(")),
                () -> assertFalse(client.contains("skillContextService.withLazyContext(")),
                () -> assertFalse(client.contains("skillContextService.withEagerContext(")),
                () -> assertFalse(client.contains("skillContextService.skillTool(")),
                () -> assertTrue(orchestrator.contains("skillContextService.withLazyContext(")),
                () -> assertTrue(orchestrator.contains("skillContextService.withEagerContext(")),
                () -> assertTrue(contentCall.contains("skillContextService.skillTool(")),
                () -> assertFalse(client.contains("private String withLazySkillContext(")),
                () -> assertFalse(client.contains("private String withEagerSkillContext(")),
                () -> assertFalse(client.contains("private Optional<ToolCallback> skillTool(")),
                () -> assertFalse(client.contains("private List<String> normalizedSkillNames(")),
                () -> assertFalse(client.contains("renderSkillSummaryContext(")),
                () -> assertFalse(client.contains("renderSkillContext(")),
                () -> assertFalse(client.contains("### 可用 Skill 与使用边界")),
                () -> assertFalse(client.contains("### 本次已加载 Skill 正文")),
                () -> assertTrue(client.lines().count() <= 220),
                () -> assertTrue(service.contains("trace.skillFrame()")),
                () -> assertTrue(service.contains("resolver().resolve(context)")),
                () -> assertTrue(service.contains("resolver().catalogTool(context)")),
                () -> assertTrue(service.contains("List<String> normalizedNames(")),
                () -> assertFalse(service.contains("Math.max(2000, maxChars)")),
                () -> assertTrue(service.contains("SKILL_RUNTIME_CONTEXT_REQUIRED")),
                () -> assertTrue(service.contains("### 本次受治理 Skill 上下文")),
                () -> assertFalse(service.contains("@Service")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("org.springframework.beans")),
                () -> assertFalse(service.contains("org.springframework.context")),
                () -> assertFalse(service.contains("org.springframework.util")),
                () -> assertTrue(service.lines().count() <= 140));
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
