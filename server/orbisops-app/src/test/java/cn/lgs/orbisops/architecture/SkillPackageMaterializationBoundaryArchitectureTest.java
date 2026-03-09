package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackageMaterializationBoundaryArchitectureTest {

    private static final String CAPABILITY =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/capability/";

    @Test
    void capabilityImportDelegatesSkillPackageMaterializationToPlainBoundary()
            throws IOException {
        String service = read(CAPABILITY + "OpsCapabilityImportService.java");
        String coordinator = read(CAPABILITY + "OpsSkillCapabilityImportCoordinator.java");
        String materializer = read(CAPABILITY + "OpsSkillPackageMaterializer.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "OpsSkillCapabilityImportCoordinator skillCoordinator")),
                () -> assertTrue(service.contains("skillCoordinator.importCapability(")),
                () -> assertTrue(service.contains(
                        "new OpsSkillCapabilityImportCoordinator(")),
                () -> assertTrue(service.contains(
                        "new OpsSkillPackageMaterializer(")),
                () -> assertTrue(coordinator.contains(
                        "OpsSkillPackageMaterializer materializer")),
                () -> assertTrue(coordinator.contains(
                        "materializer.prepare(new OpsSkillPackageMaterializer.Input(")),
                () -> assertTrue(coordinator.contains("settings.skillPackageSettings()")),
                () -> assertTrue(coordinator.contains(
                        "skillManagementUseCase.createProjectSkill(")),
                () -> assertTrue(coordinator.contains(
                        "skillCatalogQueryService.getProjectSkill(")),
                () -> assertTrue(coordinator.contains("SKILL_IMPORTED")),
                () -> assertFalse(service.contains("materializer.prepare(")),
                () -> assertFalse(service.contains("skillManagementUseCase.createProjectSkill(")),
                () -> assertFalse(service.contains("externalService.register(")),
                () -> assertFalse(service.contains(
                        "progressiveMcpProcessManager.hydrateSchema(")),
                () -> assertFalse(service.contains("JSONObject")),
                () -> assertFalse(service.contains("JSONArray")),
                () -> assertFalse(service.contains("JSON.parseObject(")),
                () -> assertFalse(service.contains("Base64")),
                () -> assertFalse(service.contains("PRIVATE_KEY")),
                () -> assertFalse(service.contains("RAW_SECRET")),
                () -> assertFalse(service.contains("AWS_ACCESS_KEY")),
                () -> assertFalse(service.contains("loadArtifact(")),
                () -> assertFalse(service.contains("assertSafeContent(")),
                () -> assertFalse(service.contains("looksLikeJson(")),
                () -> assertFalse(service.contains("isBinary(")),
                () -> assertFalse(service.contains("inferRole(")),
                () -> assertFalse(service.contains("frontMatter(")),
                () -> assertFalse(service.contains("fileName(")),
                () -> assertFalse(service.contains("stringList(")),
                () -> assertTrue(service.lines().count() <= 430),
                () -> assertTrue(materializer.contains("record Input(")),
                () -> assertTrue(materializer.contains("record Settings(")),
                () -> assertTrue(materializer.contains("record PreparedSkill(")),
                () -> assertTrue(materializer.contains(
                        "OpsCapabilityArtifactFetcher artifactFetcher")),
                () -> assertTrue(materializer.contains(
                        "OpsCapabilityImportUrlPolicy urlPolicy")),
                () -> assertTrue(materializer.contains("JSON.parseObject(")),
                () -> assertTrue(materializer.contains("JSONArray")),
                () -> assertTrue(materializer.contains("loadArtifact(")),
                () -> assertTrue(materializer.contains("Base64.getEncoder()")),
                () -> assertTrue(materializer.contains("PRIVATE_KEY")),
                () -> assertTrue(materializer.contains("RAW_SECRET")),
                () -> assertTrue(materializer.contains("AWS_ACCESS_KEY")),
                () -> assertTrue(materializer.contains("frontMatter(")),
                () -> assertTrue(materializer.contains("inferRole(")),
                () -> assertTrue(materializer.contains(
                        "OpsRuntimeHashing.canonicalHash(")),
                () -> assertTrue(materializer.contains("request.put(\"status\", \"PAUSED\")")),
                () -> assertTrue(materializer.contains(
                        "request.put(\"updateMode\", \"MANUAL_ONLY\")")),
                () -> assertFalse(materializer.contains("@Service")),
                () -> assertFalse(materializer.contains("@Component")),
                () -> assertFalse(materializer.contains("@Value")),
                () -> assertFalse(materializer.contains("@Autowired")),
                () -> assertFalse(materializer.contains("SkillManagementUseCase")),
                () -> assertFalse(materializer.contains("SkillCatalogQueryService")),
                () -> assertFalse(materializer.contains("AuthorizeProjectAccessUseCase")),
                () -> assertFalse(materializer.contains("AdminAuthService")),
                () -> assertFalse(materializer.contains("OpsConfigAuditService")),
                () -> assertFalse(materializer.contains("McpCommands")),
                () -> assertFalse(materializer.contains("ProgressiveMcpProcessManager")),
                () -> assertFalse(materializer.contains("OpsIntentDecision")),
                () -> assertTrue(materializer.lines().count() <= 340));
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
