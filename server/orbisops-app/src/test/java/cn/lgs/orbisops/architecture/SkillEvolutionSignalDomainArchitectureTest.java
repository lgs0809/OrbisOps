package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionSignalDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/service/SkillEvolutionSignalPolicy.java";
    private static final String SIGNAL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionSignalSnapshot.java";
    private static final String HINT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/model/SkillEvolutionHintSnapshot.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionSignalApplicationService.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/OpsSkillEvolutionSignalService.java";

    @Test
    void domainOwnsTypedSignalHintHashStatusAndBounds() throws IOException {
        String policy = read(POLICY);
        String signal = read(SIGNAL);
        String hint = read(HINT);

        assertAll(
                () -> assertTrue(signal.contains("record SkillEvolutionSignalSnapshot")),
                () -> assertTrue(signal.contains("String idempotencyKey")),
                () -> assertTrue(signal.contains("String payloadJson")),
                () -> assertTrue(hint.contains("record SkillEvolutionHintSnapshot")),
                () -> assertTrue(hint.contains("String contentJson")),
                () -> assertTrue(policy.contains("MessageDigest.getInstance(\"SHA-256\")")),
                () -> assertTrue(policy.contains("signalIdempotencyKey(")),
                () -> assertTrue(policy.contains("hintId(")),
                () -> assertTrue(policy.contains("\"skill-hint-\" + digest.substring(0, 32)")),
                () -> assertTrue(policy.contains("createdStatus()")),
                () -> assertTrue(policy.contains("MAX_PENDING_HINTS = 100")),
                () -> assertTrue(policy.contains("consumableHintIds(")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationUsesDomainRulesWhileLegacyServiceOnlyDelegates() throws IOException {
        String application = read(APPLICATION);
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(application.contains("SkillEvolutionSignalPolicy")),
                () -> assertTrue(application.contains("policy.draft(")),
                () -> assertTrue(application.contains("policy.signalIdempotencyKey(draft)")),
                () -> assertTrue(application.contains("policy.hintId(")),
                () -> assertTrue(application.contains("policy.createdStatus()")),
                () -> assertTrue(application.contains("policy.pendingHintLimit(limit)")),
                () -> assertTrue(application.contains("policy.consumableHintIds(hintIds)")),
                () -> assertFalse(service.contains("SkillEvolutionSignalPolicy")),
                () -> assertFalse(service.contains("MessageDigest")),
                () -> assertFalse(service.contains("StandardCharsets")),
                () -> assertFalse(service.contains("HexFormat")),
                () -> assertFalse(service.contains("private String hash(")),
                () -> assertFalse(service.contains("Math.max(1, Math.min(limit, 100))")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("JSON.toJSONString")),
                () -> assertFalse(service.contains("UUID.randomUUID()")),
                () -> assertFalse(service.contains("OpsConfigAuditService")));
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
