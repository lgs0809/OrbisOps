package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextBundleArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/contextbundle/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/runtime/contextbundle/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcRuntimeContextBundleRepository.java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsBundleModelLayerAssemblyHashAndSelectionRules() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/RuntimeContextBundlePolicy.java");
        String repository = read(DOMAIN + "adapter/repository/IRuntimeContextBundleRepository.java");

        assertAll(
                () -> assertTrue(domain.contains("record RuntimeContextBundleLayerInput")),
                () -> assertTrue(domain.contains("record RuntimeContextBundleSnapshot")),
                () -> assertTrue(domain.contains("record RuntimeContextSkillSelection")),
                () -> assertTrue(policy.contains("assembleBase")),
                () -> assertTrue(policy.contains("selectSkillRefs")),
                () -> assertTrue(policy.contains("policyRefs")),
                () -> assertTrue(policy.contains("CanonicalObjectHasher.sha256")),
                () -> assertTrue(repository.contains("RuntimeContextBundleSnapshot save")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("CREATE TABLE")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("OpsAgentChatRequest")),
                () -> assertFalse(domain.contains("SelectRuntimeSkillsQuery")));
    }

    @Test
    void applicationOwnsAuthoritativeAggregationPersistenceThenAuditAndStrictQueries() throws IOException {
        String application = readJavaTree(APPLICATION);
        String create = read(APPLICATION + "RuntimeContextBundleCreateApplicationService.java");
        String query = read(APPLICATION + "RuntimeContextBundleQueryApplicationService.java");

        assertAll(
                () -> assertTrue(create.contains("RuntimeContextSkillSelectionPort")),
                () -> assertTrue(create.contains("RuntimeContextCanarySkillPort")),
                () -> assertTrue(create.contains("RuntimeContextToolsetPort")),
                () -> assertTrue(create.contains("repository.save(candidate)")),
                () -> assertTrue(create.contains("audit.recordCreated(saved)")),
                () -> assertTrue(create.indexOf("repository.save(candidate)")
                        < create.indexOf("audit.recordCreated(saved)")),
                () -> assertTrue(query.contains("hash 不匹配")),
                () -> assertTrue(query.contains("latestCompletedForSession")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("CREATE TABLE")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("OpsAgentChatRequest")),
                () -> assertFalse(application.contains("OpsToolsetCatalogService")));
    }

    @Test
    void infrastructureExclusivelyOwnsDdlSqlJoinJsonAndMaterialization() throws IOException {
        String infrastructure = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IRuntimeContextBundleRepository")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_runtime_context_bundle")),
                () -> assertTrue(infrastructure.contains("INSERT INTO ai_ops_runtime_context_bundle")),
                () -> assertTrue(infrastructure.contains("INNER JOIN ai_ops_agent_run")),
                () -> assertTrue(infrastructure.contains("r.user_id=? AND r.status='SUCCEEDED'")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertTrue(infrastructure.contains("RuntimeContextBundleSnapshot snapshot")));
    }

    @Test
    void triggerUsesTypedAdapterAndLegacyClassesAreDeleted() throws IOException {
        String production = readJavaTree(TRIGGER);
        String workSession = read(TRIGGER + "ops/runtime/OpsWorkSessionContextPreparationService.java");
        String preparationBundle = read(TRIGGER + "ops/change/OpsPreparationContextBundleService.java");
        String changePackage = read(TRIGGER + "ops/change/OpsChangePackagePreparationService.java");

        assertAll(
                () -> assertTrue(workSession.contains("OpsRuntimeContextBundleAdapter")),
                () -> assertTrue(preparationBundle.contains("OpsRuntimeContextBundleAdapter")),
                () -> assertTrue(changePackage.contains("OpsPreparationContextBundleService")),
                () -> assertFalse(changePackage.contains("service.requireBundle(")),
                () -> assertFalse(production.contains("OpsRuntimeContextBundleService")),
                () -> assertFalse(production.contains("OpsContextAssembler")),
                () -> assertFalse(production.contains("ai_ops_runtime_context_bundle")),
                () -> assertFalse(production.contains("INSERT INTO ai_ops_runtime_context_bundle")));
    }

    private String readJavaTree(String relativeRoot) throws IOException {
        Path root = projectRoot().resolve(relativeRoot);
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
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
