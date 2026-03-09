package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkspaceViewProjectionBoundaryArchitectureTest {

    private static final String OPS =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/";
    private static final String PROJECT_APPLICATION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/project/";

    @Test
    void workspaceServiceDelegatesDetailAndCatalogProjectionAssembly()
            throws IOException {
        String service = read(OPS + "OpsProjectWorkspaceService.java");
        String assembler = read(
                PROJECT_APPLICATION + "OpsProjectWorkspaceViewAssembler.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "OpsProjectWorkspaceViewAssembler assembler = workspaceViewAssembler()")),
                () -> assertTrue(service.contains(".map(assembler::detail)")),
                () -> assertTrue(service.contains(".map(assembler::catalog)")),
                () -> assertTrue(service.contains(
                        "private OpsProjectWorkspaceViewAssembler workspaceViewAssembler()")),
                () -> assertTrue(service.contains("runtimeDirectory.loadPersisted()")),
                () -> assertTrue(service.contains("materializeProjectDefinition(")),
                () -> assertTrue(service.contains("materializeProjectResource(")),
                () -> assertTrue(service.contains("materializeProjectMcp(")),
                () -> assertTrue(service.contains("listProjectMembers(")),
                () -> assertTrue(service.contains("grantProjectMemberIfAbsent(")),
                () -> assertTrue(service.contains("replaceProjectMembers(")),
                () -> assertTrue(service.contains("templates()")),
                () -> assertFalse(service.contains(
                        "workspaceProjectionMapper.request(")),
                () -> assertFalse(service.contains(
                        "workspaceProjectionService.project(")),
                () -> assertFalse(service.contains("private Map<String, Object> projectView(")),
                () -> assertFalse(service.contains("private ProjectWorkspaceProjection projectProjection(")),
                () -> assertFalse(service.contains("authorizedLocalSkillIds(")),
                () -> assertFalse(service.contains("authorizedGlobalSkillIds(")),
                () -> assertFalse(service.contains("authorizedKnowledgeBaseIds(")),
                () -> assertFalse(service.contains("legacyDefaultKnowledgeBaseId(")),
                () -> assertFalse(service.contains("projectKnowledgeBaseEntries(")),
                () -> assertFalse(service.contains("enabledGlobalKnowledgeBaseEntries(")),
                () -> assertFalse(service.contains("projectCatalogView(")),
                () -> assertFalse(service.contains(
                        "import cn.lgs.orbisops.application.project.ProjectWorkspaceProjection;")),
                () -> assertTrue(service.lines().count() <= 275),
                () -> assertTrue(assembler.contains(
                        "public Map<String, Object> detail(ProjectDefinition project)")),
                () -> assertTrue(assembler.contains(
                        "public Map<String, Object> catalog(ProjectDefinition project)")),
                () -> assertTrue(assembler.contains("private ProjectionContext context(")),
                () -> assertTrue(assembler.contains("materializationMapper.projectView(")),
                () -> assertTrue(assembler.contains("runtimeDirectory.resources(")),
                () -> assertTrue(assembler.contains("runtimeDirectory.mcps(")),
                () -> assertTrue(assembler.contains("compatibilityPayloadStore.project(")),
                () -> assertTrue(assembler.contains(".resource(projectId, resource.resourceId())")),
                () -> assertTrue(assembler.contains(".mcp(projectId, mcp.mcpId())")),
                () -> assertTrue(assembler.contains("skillAuthorizationService.localIds(")),
                () -> assertTrue(assembler.contains("skillAuthorizationService.globalIds(")),
                () -> assertTrue(assembler.contains("knowledgeAuthorizationService.enabledIds(")),
                () -> assertTrue(assembler.contains("knowledgeAuthorizationService.projectEntries(")),
                () -> assertTrue(assembler.contains("knowledgeAuthorizationService.globalEntries(")),
                () -> assertTrue(assembler.contains("ProjectDefinition::skillIds")),
                () -> assertTrue(assembler.contains("ProjectDefinition::knowledgeBaseId")),
                () -> assertTrue(assembler.contains("projectDefinitionService.defaultKnowledgeBaseId(")),
                () -> assertTrue(assembler.contains("projectionMapper.request(")),
                () -> assertTrue(assembler.contains("projectionService.project(")),
                () -> assertTrue(assembler.contains("projectionMapper.detail(")),
                () -> assertTrue(assembler.contains("projectionMapper.catalog(")),
                () -> assertFalse(assembler.contains("@Service")),
                () -> assertFalse(assembler.contains("@Component")),
                () -> assertFalse(assembler.contains("@PostConstruct")),
                () -> assertFalse(assembler.contains("@Value")),
                () -> assertFalse(assembler.contains("@Autowired")),
                () -> assertFalse(assembler.contains("Logger")),
                () -> assertFalse(assembler.contains("ProjectMemberApplicationService")),
                () -> assertFalse(assembler.contains("initializeRuntimeDirectory(")),
                () -> assertFalse(assembler.contains("materializeProject(")),
                () -> assertFalse(assembler.contains("materializeResource(")),
                () -> assertFalse(assembler.contains("materializeMcp(")),
                () -> assertFalse(assembler.contains("templates()")),
                () -> assertTrue(assembler.lines().count() <= 180));
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
