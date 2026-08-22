package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillToolProviderBoundaryArchitectureTest {

    private static final String SKILL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/skill/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/";

    @Test
    void providerMustDelegateSettingsCatalogEditingContextAndToolProtocol() throws IOException {
        String provider = read(SKILL + "OpsSkillToolProvider.java");
        String settings = read(SKILL + "OpsSkillToolSettings.java");
        String loader = read(SKILL + "OpsSkillLocationLoader.java");
        String catalog = read(SKILL + "OpsSkillCatalog.java");
        String codec = read(SKILL + "OpsSkillMarkdownCodec.java");
        String editable = read(SKILL + "OpsSkillEditableService.java");
        String renderer = read(SKILL + "OpsSkillContextRenderer.java");
        String callback = read(SKILL + "OpsSkillToolCallbackFactory.java");
        String configuration = read(APPLICATION + "OpsSkillToolConfiguration.java");

        assertAll(
                () -> assertTrue(provider.contains("OpsSkillCatalog catalog")),
                () -> assertTrue(provider.contains("OpsSkillEditableService editableService")),
                () -> assertTrue(provider.contains("OpsSkillMarkdownCodec markdownCodec")),
                () -> assertTrue(provider.contains("OpsSkillContextRenderer contextRenderer")),
                () -> assertTrue(provider.contains("OpsSkillToolCallbackFactory callbackFactory")),
                () -> assertTrue(provider.contains("legacyConstructorDefaults()")),
                () -> assertTrue(provider.contains("catalog.loadSkills()")),
                () -> assertTrue(provider.contains("editableService.save(")),
                () -> assertTrue(provider.contains("contextRenderer.renderFull(")),
                () -> assertTrue(provider.contains("callbackFactory.build(")),
                () -> assertFalse(provider.contains("@Value")),
                () -> assertFalse(provider.contains("Skills.load")),
                () -> assertFalse(provider.contains("FunctionToolCallback.builder")),
                () -> assertFalse(provider.contains("workspacePort.")),
                () -> assertFalse(provider.contains("Path.of(")),
                () -> assertFalse(provider.contains("YAML front matter")),
                () -> assertTrue(provider.lines().count() <= 140),
                () -> assertTrue(settings.contains("public record OpsSkillToolSettings(")),
                () -> assertTrue(settings.contains("fromRaw(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(loader.contains("Skills.loadResource(resource)")),
                () -> assertTrue(loader.contains("Skills.loadDirectory(")),
                () -> assertTrue(loader.contains("boolean sameLocation(")),
                () -> assertFalse(loader.contains("@Service")),
                () -> assertTrue(catalog.contains("workspacePort.initialize(root, files)")),
                () -> assertTrue(catalog.contains("workspacePort.deletedSkillNames(")),
                () -> assertTrue(catalog.contains("new LinkedHashMap<>()")),
                () -> assertTrue(catalog.contains("if (!settings.enabled())")),
                () -> assertFalse(catalog.contains("@Service")),
                () -> assertTrue(codec.contains("String normalizeName(")),
                () -> assertTrue(codec.contains("String normalizeMarkdown(")),
                () -> assertTrue(codec.contains("SkillsTool.Skill validate(")),
                () -> assertTrue(codec.contains("String toMarkdown(")),
                () -> assertTrue(codec.contains("yamlScalar(")),
                () -> assertFalse(codec.contains("@Service")),
                () -> assertTrue(editable.contains("workspacePort.save(")),
                () -> assertTrue(editable.contains("workspacePort.delete(")),
                () -> assertFalse(editable.contains("@Service")),
                () -> assertTrue(renderer.contains("filterRequired(")),
                () -> assertTrue(renderer.contains("skill context truncated")),
                () -> assertTrue(renderer.contains("skill summary truncated")),
                () -> assertFalse(renderer.contains("@Service")),
                () -> assertTrue(callback.contains("TOOL_DESCRIPTION_TEMPLATE")),
                () -> assertTrue(callback.contains("FunctionToolCallback.builder(")),
                () -> assertTrue(callback.contains("SkillsTool.SkillsFunction")),
                () -> assertFalse(callback.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.skills.enabled")),
                () -> assertTrue(configuration.contains("orbisops.skills.locations")),
                () -> assertTrue(configuration.contains("orbisops.skills.editable-location")),
                () -> assertTrue(configuration.contains("orbisops.skills.editable-auto-init")));
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
