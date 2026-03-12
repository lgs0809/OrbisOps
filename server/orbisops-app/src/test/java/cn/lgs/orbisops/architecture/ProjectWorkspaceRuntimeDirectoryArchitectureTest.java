package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkspaceRuntimeDirectoryArchitectureTest {

    private static final String APPLICATION_ROOT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/project/";
    private static final String TRIGGER_SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsProjectWorkspaceService.java";
    private static final String LOCAL_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/toolset/OpsLocalOpsAdapterService.java";

    @Test
    void applicationOwnsTypedRuntimeDirectoryAndAtomicReload() throws IOException {
        String directory = read(APPLICATION_ROOT
                + "ProjectWorkspaceRuntimeDirectoryApplicationService.java");
        String runtimeResource = read(APPLICATION_ROOT
                + "ProjectWorkspaceRuntimeResource.java");

        assertAll(
                () -> assertTrue(directory.contains("ProjectDefinitionSnapshotPort")),
                () -> assertTrue(directory.contains("ProjectResourceSnapshotPort")),
                () -> assertTrue(directory.contains("ProjectMcpSnapshotPort")),
                () -> assertTrue(directory.contains("ProjectResourceCredentialResolutionPort")),
                () -> assertTrue(directory.contains("Map<String, ProjectDefinition>")),
                () -> assertTrue(directory.contains("Map<String, List<ProjectResourceDefinition>>")),
                () -> assertTrue(directory.contains("Map<String, List<ProjectMcpDefinition>>")),
                () -> assertTrue(directory.contains("clearInternal()")),
                () -> assertTrue(directory.contains("failurePort.loadFailed(error)")),
                () -> assertTrue(runtimeResource.contains("record ProjectWorkspaceRuntimeResource")),
                () -> assertFalse(directory.contains("org.springframework")),
                () -> assertFalse(directory.contains("JdbcTemplate")),
                () -> assertFalse(directory.contains("fastjson")),
                () -> assertFalse(directory.contains("Map<String, Object> projects")));
    }

    @Test
    void triggerFacadeNoLongerOwnsRuntimeDirectoriesOrRuntimeResourceType() throws IOException {
        String service = read(TRIGGER_SERVICE);
        String localAdapter = read(LOCAL_ADAPTER);

        assertAll(
                () -> assertTrue(service.contains("ProjectWorkspaceRuntimeDirectoryApplicationService")),
                () -> assertTrue(service.contains("OpsProjectWorkspaceMaterializationMapper")),
                () -> assertTrue(service.contains("OpsProjectWorkspaceCompatibilityPayloadStore")),
                () -> assertFalse(service.contains("private final Map<String, Map<String, Object>> projects")),
                () -> assertFalse(service.contains("generatedMcps = new LinkedHashMap")),
                () -> assertFalse(service.contains("record RuntimeResource")),
                () -> assertFalse(service.contains("loadPersistedWorkspace(")),
                () -> assertTrue(localAdapter.contains("LocalMySqlApplicationService")),
                () -> assertTrue(localAdapter.contains("LocalRedisApplicationService")),
                () -> assertFalse(localAdapter.contains("OpsProjectWorkspaceService")));
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
