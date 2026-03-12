package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillNarrowInterfaceArchitectureTest {

    private static final String SKILL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";

    @Test
    void facadeMustExposeThreeMinimalConsumerInterfaces() throws IOException {
        String runtime = read(SKILL + "SkillRuntimeToolProvider.java");
        String reader = read(SKILL + "SkillCatalogReader.java");
        String editor = read(SKILL + "SkillCatalogEditor.java");
        String facade = read(SKILL + "OpsSkillToolProvider.java");

        assertAll(
                () -> assertTrue(facade.contains("implements SkillRuntimeToolProvider, SkillCatalogReader, SkillCatalogEditor")),
                () -> assertTrue(runtime.contains("buildSkillToolCallback(")),
                () -> assertTrue(runtime.contains("renderSkillContext(")),
                () -> assertTrue(runtime.contains("renderSkillSummaryContext(")),
                () -> assertFalse(runtime.contains("saveSkill(")),
                () -> assertFalse(runtime.contains("deleteSkill(")),
                () -> assertTrue(reader.contains("loadSkills(")),
                () -> assertTrue(reader.contains("findSkill(")),
                () -> assertTrue(reader.contains("toMarkdown(")),
                () -> assertFalse(reader.contains("saveSkill(")),
                () -> assertFalse(reader.contains("deleteSkill(")),
                () -> assertTrue(editor.contains("saveSkill(")),
                () -> assertTrue(editor.contains("deleteSkill(")),
                () -> assertFalse(editor.contains("buildSkillToolCallback(")),
                () -> assertTrue(runtime.lines().count() < 30),
                () -> assertTrue(reader.lines().count() < 30),
                () -> assertTrue(editor.lines().count() < 30));
    }

    @Test
    void consumersMustDependOnTheirActualSkillCapability() throws IOException {
        String llmContext = read(TRIGGER + "ops/OpsLlmSkillContextService.java");
        String runtimeResolver = read(TRIGGER + "ops/runtime/OpsRuntimeSkillResolver.java");
        String workSessionConfig = read(TRIGGER + "ops/runtime/OpsWorkSessionRuntimeConfiguration.java");
        String fileSource = read(TRIGGER + "application/skill/OpsSkillFileSourceAdapter.java");
        String telemetry = read(TRIGGER + "http/admin/OpsTelemetryAdminController.java");
        String admin = read(TRIGGER + "http/admin/OpsSkillAdminController.java");
        String combinedRuntime = llmContext + runtimeResolver + workSessionConfig;

        assertAll(
                () -> assertTrue(combinedRuntime.contains("SkillRuntimeToolProvider")),
                () -> assertFalse(combinedRuntime.contains("OpsSkillToolProvider")),
                () -> assertTrue(fileSource.contains("ObjectProvider<SkillCatalogReader>")),
                () -> assertFalse(fileSource.contains("OpsSkillToolProvider")),
                () -> assertTrue(telemetry.contains("ObjectProvider<SkillCatalogReader>")),
                () -> assertFalse(telemetry.contains("OpsSkillToolProvider")),
                () -> assertTrue(admin.contains("ObjectProvider<SkillCatalogReader> skillCatalogReader")),
                () -> assertTrue(admin.contains("ObjectProvider<SkillCatalogEditor> skillCatalogEditor")),
                () -> assertTrue(admin.contains("ObjectProvider<SkillRuntimeToolProvider> skillRuntimeToolProvider")),
                () -> assertFalse(admin.contains("ObjectProvider<OpsSkillToolProvider>")));
    }

    @Test
    void skillSelectionMustDelegateProjectionAndBoundReferences() throws IOException {
        String selection = read(APPLICATION + "SelectRuntimeSkillsQuery.java");
        String views = read(APPLICATION + "SkillRuntimeSelectionViewMapper.java");
        String references = read(APPLICATION + "SkillBoundReferenceMapper.java");

        assertAll(
                () -> assertTrue(selection.contains("SkillRuntimeSelectionViewMapper selectionViews")),
                () -> assertTrue(selection.contains("selectionViews.result(selection)")),
                () -> assertTrue(selection.contains("selectionViews.frozenResult(")),
                () -> assertFalse(selection.contains("private Map<String, Object> runtimeVersionRef")),
                () -> assertFalse(selection.contains("private Map<String, Object> runtimeCatalogRef")),
                () -> assertFalse(selection.contains("private Map<String, Object> frozenCatalogRef")),
                () -> assertTrue(views.contains("SkillBoundReferenceMapper boundReferences")),
                () -> assertTrue(views.contains("runtimeCatalogRef(")),
                () -> assertTrue(views.contains("suppressedRef(")),
                () -> assertTrue(references.contains("runtimeVersionRef(")),
                () -> assertTrue(references.contains("frozenCatalogRef(")),
                () -> assertFalse(selection.contains("McpRuntime")),
                () -> assertFalse(views.contains("McpRuntime")),
                () -> assertFalse(references.contains("McpRuntime")));
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
