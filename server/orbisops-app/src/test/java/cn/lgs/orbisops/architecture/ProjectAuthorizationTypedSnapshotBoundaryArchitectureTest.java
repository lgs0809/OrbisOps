package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAuthorizationTypedSnapshotBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/project/";
    private static final String TRIGGER_PROJECT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/project/";
    private static final String TRIGGER_AGENT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/http/agent/";

    @Test
    void authorizationMustUseTypedProjectSnapshotsAndProjectDefinitions() throws IOException {
        String accessPort = read(APPLICATION + "ProjectAccessPort.java");
        String accessUseCase = read(APPLICATION + "AuthorizeProjectAccessUseCase.java");
        String definitions = read(APPLICATION + "ProjectDefinitionApplicationService.java");
        String knowledge = read(APPLICATION + "ProjectKnowledgeAuthorizationApplicationService.java");
        String mcp = read(APPLICATION + "ProjectMcpAuthorizationApplicationService.java");
        String skill = read(APPLICATION + "ProjectSkillAuthorizationApplicationService.java");
        String adapter = read(TRIGGER_PROJECT + "OpsProjectAccessAdapter.java");
        String mapper = read(TRIGGER_PROJECT + "OpsProjectCatalogViewMapper.java");
        String chatController = read(TRIGGER_AGENT + "OpsChatController.java");
        String userChatController = read(TRIGGER_AGENT + "OpsUserChatController.java");

        assertAll(
                () -> assertTrue(accessPort.contains("List<ProjectCatalogEntry> publicCatalog()")),
                () -> assertFalse(accessPort.contains("Map<String, Object>")),
                () -> assertTrue(accessUseCase.contains("List<ProjectCatalogEntry> catalog(")),
                () -> assertTrue(accessUseCase.contains("project.projectId()")),
                () -> assertFalse(accessUseCase.contains("project.get(")),
                () -> assertTrue(definitions.contains("Optional<ProjectDefinition> findDefinition(")),
                () -> assertTrue(definitions.contains("List<ProjectDefinition> listEnabledDefinitions()")),
                () -> assertTrue(knowledge.contains("project.knowledgeBaseId()")),
                () -> assertTrue(mcp.contains("project.sharedMcpIds()")),
                () -> assertTrue(skill.contains("project.skillIds()")),
                () -> assertFalse(knowledge.contains("definitionService.find(")),
                () -> assertFalse(mcp.contains("definitionService.find(")),
                () -> assertFalse(skill.contains("definitionService.find(")),
                () -> assertFalse(knowledge.contains("project.get(")),
                () -> assertFalse(mcp.contains("project.get(")),
                () -> assertFalse(skill.contains("project.get(")),
                () -> assertTrue(adapter.contains("projectDefinitionService.listEnabledDefinitions()")),
                () -> assertTrue(adapter.contains("ProjectCatalogEntry::from")),
                () -> assertTrue(mapper.contains("Trigger compatibility projection")),
                () -> assertTrue(chatController.contains("OpsProjectCatalogViewMapper.views(")),
                () -> assertTrue(userChatController.contains("OpsProjectCatalogViewMapper.views(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
