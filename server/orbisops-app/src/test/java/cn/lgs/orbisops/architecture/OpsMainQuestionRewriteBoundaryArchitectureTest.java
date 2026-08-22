package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainQuestionRewriteBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void rewriteFacadeDelegatesTypedBoundsPromptAndJsonProjection() throws IOException {
        String service = read(RUNTIME + "OpsMainQuestionRewriteService.java");
        String settings = read(RUNTIME + "OpsMainQuestionRewriteSettings.java");
        String protocol = read(RUNTIME + "OpsMainQuestionRewriteProtocol.java");
        String configuration = read(APPLICATION + "OpsMainQuestionRewriteConfiguration.java");

        assertAll(
                () -> assertTrue(service.contains("OpsMainQuestionRewriteSettings settings")),
                () -> assertTrue(service.contains("OpsMainQuestionRewriteProtocol protocol")),
                () -> assertTrue(service.contains("protocol.systemPrompt()")),
                () -> assertTrue(service.contains("protocol.userPrompt(")),
                () -> assertTrue(service.contains("protocol.project(")),
                () -> assertTrue(service.contains("@Autowired")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("JSONArray")),
                () -> assertFalse(service.contains("private String buildSystemPrompt(")),
                () -> assertFalse(service.contains("private List<String> resolvedReferences(")),
                () -> assertTrue(service.lines().count() <= 85),
                () -> assertTrue(settings.contains("public record OpsMainQuestionRewriteSettings(")),
                () -> assertTrue(settings.contains("rewrittenQuestionLimit()")),
                () -> assertTrue(protocol.contains("String systemPrompt()")),
                () -> assertTrue(protocol.contains("RewriteResult project(")),
                () -> assertTrue(protocol.contains("resolvedReferences")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.chat.query-rewrite.enabled")),
                () -> assertTrue(configuration.contains("orbisops.chat.query-rewrite.max-memory-chars")),
                () -> assertTrue(configuration.contains("orbisops.chat.query-rewrite.max-question-chars")));
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
