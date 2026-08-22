package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsCapabilityImportBoundaryArchitectureTest {

    private static final String CAPABILITY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/capability/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/capability/";

    @Test
    void importFacadeDelegatesAccessSkillMcpAndTypedOutboundPolicy() throws IOException {
        String facade = read(CAPABILITY + "OpsCapabilityImportService.java");
        String settings = read(CAPABILITY + "OpsCapabilityImportSettings.java");
        String access = read(CAPABILITY + "OpsCapabilityImportAccessPolicy.java");
        String skill = read(CAPABILITY + "OpsSkillCapabilityImportCoordinator.java");
        String mcp = read(CAPABILITY + "OpsMcpCapabilityImportCoordinator.java");
        String urlPolicy = read(CAPABILITY + "OpsCapabilityImportUrlPolicy.java");
        String fetcher = read(CAPABILITY + "OpsCapabilityArtifactFetcher.java");
        String configuration = read(APPLICATION + "OpsCapabilityImportConfiguration.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsCapabilityImportAccessPolicy accessPolicy")),
                () -> assertTrue(facade.contains("OpsSkillCapabilityImportCoordinator skillCoordinator")),
                () -> assertTrue(facade.contains("OpsMcpCapabilityImportCoordinator mcpCoordinator")),
                () -> assertTrue(facade.contains("legacyConstructorDefaults()")),
                () -> assertTrue(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("SkillManagementUseCase skillManagementUseCase;")),
                () -> assertFalse(facade.contains("OpsConfigAuditService auditService;")),
                () -> assertTrue(facade.contains("public Map<String, Object> importSkill(")),
                () -> assertTrue(facade.contains("public Map<String, Object> importMcp(")),
                () -> assertFalse(facade.contains("private void assertCanManage(")),
                () -> assertTrue(settings.contains("public record OpsCapabilityImportSettings(")),
                () -> assertTrue(settings.contains("skillPackageSettings()")),
                () -> assertTrue(access.contains("ProjectAction.MANAGE_CAPABILITY")),
                () -> assertTrue(access.contains("projectAccessUseCase.requireAction(")),
                () -> assertTrue(skill.contains("skillManagementUseCase.createProjectSkill(")),
                () -> assertTrue(skill.contains("SKILL_IMPORTED")),
                () -> assertTrue(mcp.contains("importer.importCapability(")),
                () -> assertTrue(mcp.contains("MCP_REGISTERED")),
                () -> assertTrue(urlPolicy.contains("OpsCapabilityImportSettings settings")),
                () -> assertFalse(urlPolicy.contains("@Value")),
                () -> assertTrue(fetcher.contains("settings.fetchTimeoutSeconds()")),
                () -> assertFalse(fetcher.contains("@Value")),
                () -> assertTrue(configuration.contains("orbisops.capability-import.max-artifact-bytes")),
                () -> assertTrue(configuration.contains("orbisops.capability-import.allowed-hosts")));
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
