package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmRuntimePolicyBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/llm/";

    @Test
    void llmClientDelegatesRuntimeStatusAndDegradationPolicy() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String policy = read(OPS + "OpsLlmRuntimePolicy.java");
        String settings = read(DOMAIN + "model/LlmRuntimeSettings.java");
        String mode = read(DOMAIN + "model/LlmDegradationMode.java");
        String domainPolicy = read(DOMAIN + "service/LlmDegradationPolicy.java");

        assertAll(
                () -> assertTrue(client.contains("OpsLlmRuntimePolicy runtimePolicy")),
                () -> assertTrue(client.contains("runtimePolicy.status(")),
                () -> assertTrue(client.contains("runtimePolicy.reject(")),
                () -> assertTrue(client.contains("runtimePolicy.degrade(")),
                () -> assertTrue(client.contains("settings.policySettings()")),
                () -> assertFalse(client.contains("runtimeSettings()")),
                () -> assertFalse(client.contains("new OpsLlmRuntimePolicy.Settings(")),
                () -> assertFalse(client.contains("data.put(\"failOnLlmDegradation\"")),
                () -> assertFalse(client.contains("data.put(\"jsonRepairRetryEnabled\"")),
                () -> assertFalse(client.contains("data.put(\"jsonResponseFormatEnabled\"")),
                () -> assertFalse(client.contains("data.put(\"jsonMaxCompletionTokens\"")),
                () -> assertFalse(client.contains("data.put(\"jsonSkillContextRetryEnabled\"")),
                () -> assertFalse(client.contains("data.put(\"jsonSkillContextRetryMaxChars\"")),
                () -> assertFalse(client.contains("data.put(\"modelCallTimeoutSeconds\"")),
                () -> assertFalse(client.contains("log.warn(")),
                () -> assertFalse(client.contains("@Slf4j")),
                () -> assertFalse(client.contains("lombok.extern.slf4j.Slf4j")),
                () -> assertTrue(client.lines().count() <= 210),
                () -> assertTrue(policy.contains("LlmDegradationPolicy domainPolicy")),
                () -> assertTrue(policy.contains("LlmRuntimeSettings settings")),
                () -> assertTrue(policy.contains("domainPolicy.decide(settings)")),
                () -> assertTrue(policy.contains("Map<String, Object> status(")),
                () -> assertTrue(policy.contains("void reject(")),
                () -> assertTrue(policy.contains("void degrade(")),
                () -> assertFalse(policy.contains("record Settings(")),
                () -> assertTrue(policy.contains("\"failOnLlmDegradation\"")),
                () -> assertTrue(policy.contains("\"jsonRepairRetryEnabled\"")),
                () -> assertTrue(policy.contains("\"jsonResponseFormatEnabled\"")),
                () -> assertTrue(policy.contains("\"jsonMaxCompletionTokens\"")),
                () -> assertTrue(policy.contains("\"jsonSkillContextRetryEnabled\"")),
                () -> assertTrue(policy.contains("\"jsonSkillContextRetryMaxChars\"")),
                () -> assertTrue(policy.contains("\"modelCallTimeoutSeconds\"")),
                () -> assertTrue(policy.contains("{} LLM 降级到规则逻辑：{}")),
                () -> assertTrue(policy.contains("{} LLM 调用失败，降级到规则逻辑：{}")),
                () -> assertFalse(policy.contains("@Service")),
                () -> assertFalse(policy.contains("@Component")),
                () -> assertFalse(policy.contains("@Value")),
                () -> assertFalse(policy.contains("ApplicationContext")),
                () -> assertFalse(policy.contains("ObjectProvider")),
                () -> assertTrue(policy.lines().count() <= 100),
                () -> assertTrue(settings.contains("public record LlmRuntimeSettings(")),
                () -> assertTrue(mode.contains("public enum LlmDegradationMode")),
                () -> assertTrue(domainPolicy.contains("public final class LlmDegradationPolicy")),
                () -> assertTrue(domainPolicy.contains("LlmDegradationMode.FAIL_CLOSED")),
                () -> assertTrue(domainPolicy.contains("LlmDegradationMode.RULE_FALLBACK")),
                () -> assertFalse(settings.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domainPolicy.contains("org.slf4j")),
                () -> assertFalse(domainPolicy.contains("org.springframework")));
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
