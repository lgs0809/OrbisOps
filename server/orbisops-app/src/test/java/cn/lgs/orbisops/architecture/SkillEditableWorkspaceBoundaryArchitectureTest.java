package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEditableWorkspaceBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/skill/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/skill/";
    private static final String SKILL =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/skill/";
    private static final String PROVIDER = SKILL + "OpsSkillToolProvider.java";
    private static final String CATALOG = SKILL + "OpsSkillCatalog.java";
    private static final String EDITABLE = SKILL + "OpsSkillEditableService.java";
    private static final String CODEC = SKILL + "OpsSkillMarkdownCodec.java";
    private static final String LOADER = SKILL + "OpsSkillLocationLoader.java";
    private static final String CALLBACK = SKILL + "OpsSkillToolCallbackFactory.java";

    @Test
    void triggerBoundariesMustKeepSdkProtocolAndDelegateFileWorkspace()
            throws IOException {
        String provider = read(PROVIDER);
        String catalog = read(CATALOG);
        String editable = read(EDITABLE);
        String codec = read(CODEC);
        String loader = read(LOADER);
        String callback = read(CALLBACK);
        String trigger = provider + catalog + editable + codec + loader + callback;

        assertAll(
                () -> assertTrue(provider.contains("OpsSkillCatalog catalog")),
                () -> assertTrue(provider.contains("OpsSkillEditableService editableService")),
                () -> assertTrue(provider.contains("OpsSkillToolCallbackFactory callbackFactory")),
                () -> assertFalse(provider.contains("private final SkillEditableWorkspacePort")),
                () -> assertTrue(provider.contains("new OpsSkillEditableService(")),
                () -> assertTrue(editable.contains("SkillEditableWorkspacePort workspacePort")),
                () -> assertTrue(editable.contains("workspacePort.save(")),
                () -> assertTrue(editable.contains("workspacePort.delete(")),
                () -> assertTrue(catalog.contains("workspacePort.initialize(")),
                () -> assertTrue(codec.contains("workspacePort.createValidationWorkspace(")),
                () -> assertTrue(loader.contains("Skills.loadDirectory(")),
                () -> assertTrue(callback.contains("FunctionToolCallback.builder(")),
                () -> assertTrue(trigger.contains("SkillsTool.Skill")),
                () -> assertFalse(trigger.contains("Files.")),
                // Location/SDK protocol decoding may read bounded UTF-8 resources. All
                // editable mutations and backups still belong to the workspace adapter.
                () -> assertTrue(loader.contains("new SafeConstructor(options)")),
                () -> assertTrue(loader.contains("readNBytes(256 * 1024 + 1)")),
                () -> assertFalse((provider + catalog + editable + codec + callback).contains("StandardCharsets")),
                () -> assertFalse(loader.contains("getOutputStream(")),
                () -> assertFalse(trigger.contains("createTempDirectory")),
                () -> assertFalse(trigger.contains("BACKUP_TIME_FORMATTER")),
                () -> assertFalse(trigger.contains("DELETE_MARKER")),
                () -> assertFalse(trigger.contains("deleteRecursively(")),
                () -> assertFalse(trigger.contains("@jakarta.annotation.Resource")));
    }

    @Test
    void applicationWorkspaceBoundaryMustRemainFrameworkAndSdkNeutral()
            throws IOException {
        String application = read(APPLICATION + "SkillEditableFile.java")
                + read(APPLICATION + "SkillEditableWorkspacePort.java");

        assertAll(
                () -> assertTrue(application.contains("SkillEditableFile")),
                () -> assertTrue(application.contains("resolveRoot(")),
                () -> assertTrue(application.contains("createValidationWorkspace(")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("java.nio.file")),
                () -> assertFalse(application.contains("SkillsTool")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void infrastructureMustOwnEditableFileBackupMarkersAndTempWorkspace()
            throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "FileSkillEditableWorkspaceAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains(
                        "implements SkillEditableWorkspacePort")),
                () -> assertTrue(adapter.contains("Files.createDirectories(")),
                () -> assertTrue(adapter.contains("Files.copy(")),
                () -> assertTrue(adapter.contains("Files.move(")),
                () -> assertTrue(adapter.contains("Files.writeString(")),
                () -> assertTrue(adapter.contains("Files.createTempDirectory(")),
                () -> assertTrue(adapter.contains("Files.walk(")),
                () -> assertTrue(adapter.contains("DELETE_MARKER")),
                () -> assertTrue(adapter.contains("BACKUP_TIME_FORMATTER")),
                () -> assertTrue(adapter.contains("assertInside(")),
                () -> assertFalse(adapter.contains("SkillsTool")),
                () -> assertFalse(adapter.contains("Skills.loadDirectory")),
                () -> assertFalse(adapter.contains("ToolCallback")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void workspacePortMustHaveOneProductionImplementation()
            throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "FileSkillEditableWorkspaceAdapter.java");
        assertAll(
                () -> assertTrue(adapter.contains("@Repository")),
                () -> assertTrue(adapter.contains(
                        "implements SkillEditableWorkspacePort")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(
                current.resolve("orbisops-application"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(
                parent.resolve("orbisops-application"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(
                nested.resolve("orbisops-application"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
