package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionSignalApplicationArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionSignalApplicationService.java";
    private static final String ID_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionSignalIdPort.java";
    private static final String AUDIT_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/SkillEvolutionSignalAuditPort.java";
    private static final String ID_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionSignalIdentityAdapter.java";
    private static final String AUDIT_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionSignalAuditAdapter.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillEvolutionSignalMapper.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/OpsSkillEvolutionSignalService.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/OpsSkillCatalogApplicationConfiguration.java";

    @Test
    void applicationOwnsSignalHintLifecycleAndDependsOnlyOnTypedPorts() throws IOException {
        String application = read(APPLICATION);
        String idPort = read(ID_PORT);
        String auditPort = read(AUDIT_PORT);

        assertAll(
                () -> assertTrue(application.contains("ISkillEvolutionSignalRepository")),
                () -> assertTrue(application.contains("SkillEvolutionSignalPolicy")),
                () -> assertTrue(application.contains("SkillEvolutionSignalIdPort")),
                () -> assertTrue(application.contains("SkillEvolutionSignalAuditPort")),
                () -> assertTrue(application.contains("repository.saveIdempotent(")),
                () -> assertTrue(application.contains("repository.saveHintIdempotent(")),
                () -> assertTrue(application.contains("repository.findPendingHints(")),
                () -> assertTrue(application.contains("repository.markHintConsumed(")),
                () -> assertTrue(application.contains("auditPort.recordSignalCreated(")),
                () -> assertTrue(application.contains("auditPort.recordHintsConsumed(")),
                () -> assertTrue(idPort.contains("interface SkillEvolutionSignalIdPort")),
                () -> assertTrue(auditPort.contains("interface SkillEvolutionSignalAuditPort")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("UUID")),
                () -> assertFalse(application.contains("OpsConfigAuditService")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("Map<String, Object>")));
    }

    @Test
    void triggerAdaptersOwnUuidAuditFastJsonAndLegacyProjection() throws IOException {
        String idAdapter = read(ID_ADAPTER);
        String auditAdapter = read(AUDIT_ADAPTER);
        String mapper = read(MAPPER);

        assertAll(
                () -> assertTrue(idAdapter.contains("implements SkillEvolutionSignalIdPort")),
                () -> assertTrue(idAdapter.contains("UUID.randomUUID()")),
                () -> assertTrue(auditAdapter.contains("implements SkillEvolutionSignalAuditPort")),
                () -> assertTrue(auditAdapter.contains("OpsConfigAuditService")),
                () -> assertTrue(auditAdapter.contains("recordRuntimeEvent(")),
                () -> assertTrue(auditAdapter.contains("hints-consumed")),
                () -> assertTrue(mapper.contains("JSON.toJSONString")),
                () -> assertTrue(mapper.contains("JSON.parseObject")),
                () -> assertTrue(mapper.contains("SkillEvolutionSignalCommand signalCommand(")),
                () -> assertTrue(mapper.contains("SkillEvolutionHintCommand hintCommand(")),
                () -> assertTrue(mapper.contains("Map<String, Object> signalView(")),
                () -> assertTrue(mapper.contains("List<Map<String, Object>> pendingHintViews(")),
                () -> assertFalse(mapper.contains("ISkillEvolutionSignalRepository")),
                () -> assertFalse(mapper.contains("SkillEvolutionSignalPolicy")));
    }

    @Test
    void legacyServiceOnlyMapsAndDelegatesToApplication() throws IOException {
        String facade = read(FACADE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("SkillEvolutionSignalApplicationService")),
                () -> assertTrue(facade.contains("OpsSkillEvolutionSignalMapper")),
                () -> assertTrue(facade.contains("applicationService.record(mapper.signalCommand(")),
                () -> assertTrue(facade.contains("applicationService.createHint(mapper.hintCommand(")),
                () -> assertTrue(facade.contains("applicationService.pendingHints(")),
                () -> assertTrue(facade.contains("applicationService.markHintsConsumed(")),
                () -> assertFalse(facade.contains("ISkillEvolutionSignalRepository")),
                () -> assertFalse(facade.contains("SkillEvolutionSignalPolicy")),
                () -> assertFalse(facade.contains("JSON.")),
                () -> assertFalse(facade.contains("UUID")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("new SkillEvolutionSignalSnapshot(")),
                () -> assertTrue(configuration.contains("skillEvolutionSignalApplicationService(")),
                () -> assertTrue(configuration.contains("new SkillEvolutionSignalApplicationService(")));
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
