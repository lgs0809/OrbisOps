package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/evidence/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/evidence/";
    private static final String INFRA = "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedToolResultEvidenceAndTrustedProofRules() throws IOException {
        String source = readTree(DOMAIN);
        assertAll(
                () -> assertTrue(source.contains("record ToolResult(")),
                () -> assertTrue(source.contains("record EvidenceRecord(")),
                () -> assertTrue(source.contains("record TrustedProof(")),
                () -> assertTrue(source.contains("class ToolResultPolicy")),
                () -> assertTrue(source.contains("class EvidencePolicy")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("CREATE TABLE")));
    }

    @Test
    void applicationOwnsTypedUseCasesAndPortsOnly() throws IOException {
        String source = readTree(APPLICATION);
        assertAll(
                () -> assertTrue(source.contains("class ToolResultApplicationService")),
                () -> assertTrue(source.contains("class EvidenceApplicationService")),
                () -> assertTrue(source.contains("class TrustedProofApplicationService")),
                () -> assertTrue(source.contains("class EvidenceIdentityFactory")),
                () -> assertFalse(source.contains("EvidenceIdentityPort")),
                () -> assertTrue(source.contains("EvidenceTransactionPort")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("CREATE TABLE")));
    }

    @Test
    void infrastructureExclusivelyOwnsThreeTablesAndJsonPersistence() throws IOException {
        String source = readFiles(List.of(
                INFRA + "JdbcEvidenceSchemaInitializer.java",
                INFRA + "JdbcToolResultRepository.java",
                INFRA + "JdbcEvidenceRepository.java",
                INFRA + "JdbcTrustedProofRepository.java"));
        assertAll(
                () -> assertTrue(source.contains("ai_ops_tool_result")),
                () -> assertTrue(source.contains("ai_ops_evidence")),
                () -> assertTrue(source.contains("ai_ops_trusted_proof")),
                () -> assertTrue(source.contains("JdbcTemplate")),
                () -> assertTrue(source.contains("JSON.toJSONString")),
                () -> assertTrue(source.contains("implements IToolResultRepository")),
                () -> assertTrue(source.contains("implements IEvidenceRepository")),
                () -> assertTrue(source.contains("implements ITrustedProofRepository")));
    }

    @Test
    void legacyStoresAreThinCompatibilityAcls() throws IOException {
        String result = read(TRIGGER + "ops/toolset/OpsToolResultStore.java");
        String evidence = read(TRIGGER + "ops/toolset/OpsEvidenceStore.java");
        String proof = read(TRIGGER + "ops/toolset/OpsTrustedProofService.java");
        String combined = result + evidence + proof;
        assertAll(
                () -> assertTrue(result.contains("ToolResultApplicationService")),
                () -> assertTrue(evidence.contains("EvidenceApplicationService")),
                () -> assertTrue(proof.contains("TrustedProofApplicationService")),
                () -> assertFalse(combined.contains("JdbcTemplate")),
                () -> assertFalse(combined.contains("CREATE TABLE")),
                () -> assertFalse(combined.contains("com.alibaba.fastjson")),
                () -> assertFalse(combined.contains("MessageDigest")),
                () -> assertFalse(combined.contains("ConcurrentHashMap")),
                () -> assertTrue(Files.readAllLines(projectRoot().resolve(
                        TRIGGER + "ops/toolset/OpsToolResultStore.java")).size() < 130),
                () -> assertTrue(Files.readAllLines(projectRoot().resolve(
                        TRIGGER + "ops/toolset/OpsEvidenceStore.java")).size() < 90),
                () -> assertTrue(Files.readAllLines(projectRoot().resolve(
                        TRIGGER + "ops/toolset/OpsTrustedProofService.java")).size() < 100));
    }

    private String readTree(String relativeRoot) throws IOException {
        try (var stream = Files.walk(projectRoot().resolve(relativeRoot))) {
            StringBuilder source = new StringBuilder();
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
            return source.toString();
        }
    }

    private String readFiles(List<String> paths) throws IOException {
        StringBuilder source = new StringBuilder();
        for (String path : paths) source.append(read(path)).append('\n');
        return source.toString();
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
