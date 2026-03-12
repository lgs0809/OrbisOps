package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCapabilityImportBoundaryArchitectureTest {

    private static final String CAPABILITY =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/capability/";

    @Test
    void capabilityImportDelegatesMcpRegistrationDiscoveryAndHydration()
            throws IOException {
        String service = read(CAPABILITY + "OpsCapabilityImportService.java");
        String coordinator = read(CAPABILITY + "OpsMcpCapabilityImportCoordinator.java");
        String importer = read(CAPABILITY + "OpsMcpCapabilityImporter.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "OpsMcpCapabilityImportCoordinator mcpCoordinator")),
                () -> assertTrue(service.contains(
                        "mcpCoordinator.importCapability(")),
                () -> assertTrue(service.contains(
                        "new OpsMcpCapabilityImportCoordinator(")),
                () -> assertTrue(service.contains(
                        "new OpsMcpCapabilityImporter(")),
                () -> assertTrue(service.contains(
                        "new OpsSkillPackageMaterializer(")),
                () -> assertTrue(service.contains("accessPolicy.assertCanManage(")),
                () -> assertTrue(coordinator.contains(
                        "OpsMcpCapabilityImporter importer")),
                () -> assertTrue(coordinator.contains(
                        "importer.importCapability(")),
                () -> assertTrue(coordinator.contains(
                        "new OpsMcpCapabilityImporter.Input(")),
                () -> assertTrue(coordinator.contains("MCP_REGISTERED")),
                () -> assertTrue(coordinator.contains("DISCOVERY_FAILED")),
                () -> assertFalse(service.contains("McpCommands")),
                () -> assertFalse(service.contains("OpsRuntimeHashing")),
                () -> assertFalse(service.contains("OpsMcpServerConfig")),
                () -> assertFalse(service.contains("java.net.URI")),
                () -> assertFalse(service.contains("new ArrayList")),
                () -> assertFalse(service.contains("Locale")),
                () -> assertFalse(service.contains("externalService.register(")),
                () -> assertFalse(service.contains("resolveForDiscovery(")),
                () -> assertFalse(service.contains(
                        "inspectRemoteToolDefinitions(")),
                () -> assertFalse(service.contains("hydrateSchema(")),
                () -> assertFalse(service.contains("recordDiscovery(")),
                () -> assertFalse(service.contains(
                        "requireExternalMcpService(")),
                () -> assertFalse(service.contains(
                        "requireProjectMcpRuntimeConfigService(")),
                () -> assertFalse(service.contains("private String slug(")),
                () -> assertTrue(service.lines().count() <= 300),
                () -> assertTrue(importer.contains("record Input(")),
                () -> assertTrue(importer.contains("record Result(")),
                () -> assertTrue(importer.contains(
                        "OpsCapabilityImportUrlPolicy urlPolicy")),
                () -> assertTrue(importer.contains(
                        "ProjectExternalMcpApplicationService externalMcpService")),
                () -> assertTrue(importer.contains(
                        "OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService")),
                () -> assertTrue(importer.contains(
                        "OpsMcpToolProvider mcpToolProvider")),
                () -> assertTrue(importer.contains(
                        "ProgressiveMcpProcessManager progressiveMcpProcessManager")),
                () -> assertTrue(importer.contains("urlPolicy.validate(")),
                () -> assertTrue(importer.contains(
                        "OpsRuntimeHashing.canonicalHash(")),
                () -> assertTrue(importer.contains("externalService.register(")),
                () -> assertTrue(importer.contains("resolveForDiscovery(")),
                () -> assertTrue(importer.contains(
                        "inspectRemoteToolDefinitions(")),
                () -> assertTrue(importer.contains("hydrateSchema(")),
                () -> assertTrue(importer.contains("recordDiscovery(")),
                () -> assertTrue(importer.contains(
                        "McpSchemaHydrationRequest")),
                () -> assertTrue(importer.contains(
                        "DISCOVERED_PENDING_REVIEW")),
                () -> assertTrue(importer.contains("DISCOVERY_FAILED")),
                () -> assertFalse(importer.contains("@Service")),
                () -> assertFalse(importer.contains("@Component")),
                () -> assertFalse(importer.contains("@Value")),
                () -> assertFalse(importer.contains("@Autowired")),
                () -> assertFalse(importer.contains("SkillManagementUseCase")),
                () -> assertFalse(importer.contains("SkillCatalogQueryService")),
                () -> assertFalse(importer.contains(
                        "AuthorizeProjectAccessUseCase")),
                () -> assertFalse(importer.contains("AdminAuthService")),
                () -> assertFalse(importer.contains("OpsConfigAuditService")),
                () -> assertFalse(importer.contains("OpsIntentDecision")),
                () -> assertTrue(importer.lines().count() <= 240));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
