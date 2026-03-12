package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAuditArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/audit/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/audit/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedAuditPolicyMaskingAndRepositoryPorts() throws IOException {
        String source = readFiles(List.of(
                DOMAIN + "model/ConfigAuditDraft.java",
                DOMAIN + "model/ConfigAuditEntry.java",
                DOMAIN + "model/ConfigAuditCriteria.java",
                DOMAIN + "model/ConfigAuditPolicySnapshot.java",
                DOMAIN + "model/ConfigAuditReadiness.java",
                DOMAIN + "adapter/repository/IConfigAuditRepository.java",
                DOMAIN + "adapter/repository/IConfigAuditPolicyRepository.java",
                DOMAIN + "service/ConfigAuditRiskPolicy.java",
                DOMAIN + "service/ConfigAuditMaskingPolicy.java"));

        assertAll(
                () -> assertTrue(source.contains("record ConfigAuditDraft")),
                () -> assertTrue(source.contains("record ConfigAuditEntry")),
                () -> assertTrue(source.contains("record ConfigAuditCriteria")),
                () -> assertTrue(source.contains("interface IConfigAuditRepository")),
                () -> assertTrue(source.contains("interface IConfigAuditPolicyRepository")),
                () -> assertTrue(source.contains("class ConfigAuditRiskPolicy")),
                () -> assertTrue(source.contains("class ConfigAuditMaskingPolicy")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("ai_ops_config_audit")),
                () -> assertFalse(source.contains("ai_ops_audit_policy")));
    }

    @Test
    void applicationOwnsTypedCommandQueryPolicyAndReadinessOrchestration() throws IOException {
        String source = readFiles(List.of(
                APPLICATION + "ConfigAuditCommand.java",
                APPLICATION + "AuditQuery.java",
                APPLICATION + "ConfigAuditApplicationService.java"));

        assertAll(
                () -> assertTrue(source.contains("record ConfigAuditCommand")),
                () -> assertTrue(source.contains("ConfigAuditDraft")),
                () -> assertTrue(source.contains("IConfigAuditRepository")),
                () -> assertTrue(source.contains("IConfigAuditPolicyRepository")),
                () -> assertTrue(source.contains("ConfigAuditPolicySnapshot")),
                () -> assertTrue(source.contains("ConfigAuditReadiness")),
                () -> assertTrue(source.contains("recordIdempotent")),
                () -> assertTrue(source.contains("UUID.nameUUIDFromBytes")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("Map<String, Object> request")),
                () -> assertFalse(source.contains("ai_ops_config_audit")),
                () -> assertFalse(source.contains("ai_ops_audit_policy")));
    }

    @Test
    void infrastructureExclusivelyOwnsAuditPolicySqlDdlRowsAndFallback() throws IOException {
        String audit = read(INFRASTRUCTURE + "JdbcConfigAuditRepository.java");
        String policy = read(INFRASTRUCTURE + "JdbcConfigAuditPolicyRepository.java");
        String schema = read(INFRASTRUCTURE + "JdbcConfigAuditSchemaInitializer.java");

        assertAll(
                () -> assertTrue(audit.contains("implements IConfigAuditRepository")),
                () -> assertTrue(audit.contains("ai_ops_config_audit")),
                () -> assertTrue(audit.contains("RowMapper<ConfigAuditEntry>")),
                () -> assertTrue(audit.contains("DEGRADED_MEMORY")),
                () -> assertTrue(audit.contains("禁止内存审计 fallback")),
                () -> assertTrue(audit.contains("DuplicateKeyException")),
                () -> assertTrue(audit.contains("find(draft.auditId())")),
                () -> assertTrue(policy.contains("implements IConfigAuditPolicyRepository")),
                () -> assertTrue(policy.contains("ai_ops_audit_policy")),
                () -> assertTrue(policy.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_config_audit")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_audit_policy")),
                () -> assertTrue(schema.contains("information_schema.COLUMNS")),
                () -> assertTrue(schema.contains("information_schema.STATISTICS")));
    }

    @Test
    void triggerFacadeContainsOnlyCompatibilityAclAndNoPersistenceMechanics() throws IOException {
        String facade = read(TRIGGER + "ops/OpsConfigAuditService.java");
        String mapper = read(TRIGGER + "application/audit/OpsConfigAuditMapper.java");
        String controller = read(TRIGGER + "http/admin/OpsConfigAuditAdminController.java");

        assertAll(
                () -> assertTrue(facade.contains("ConfigAuditApplicationService")),
                () -> assertTrue(facade.contains("OpsConfigAuditMapper")),
                () -> assertTrue(mapper.contains("ConfigAuditCommand")),
                () -> assertTrue(mapper.contains("AdminAuthService.AuthPrincipal")),
                () -> assertTrue(controller.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("JdbcTemplate")),
                () -> assertFalse(facade.contains("DataAccessException")),
                () -> assertFalse(facade.contains("@PostConstruct")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("ai_ops_config_audit")),
                () -> assertFalse(facade.contains("ai_ops_audit_policy")),
                () -> assertFalse(facade.contains("CREATE TABLE")),
                () -> assertFalse(facade.contains("information_schema")));
    }

    @Test
    void obsoleteReverseAdapterChainIsPhysicallyAbsent() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/audit/ConfigAuditPort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/audit/OpsConfigAuditAdapter.java"))));
    }

    private String readFiles(List<String> paths) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String path : paths) result.append(read(path)).append('\n');
        return result.toString();
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
