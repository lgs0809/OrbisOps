package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.core.io.DefaultResourceLoader;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillMarkdownCodecTest {

    private final OpsSkillMarkdownCodec codec = new OpsSkillMarkdownCodec();

    @Test
    void realSdkRoundTripRetainsStructuredRoutingAndNestedMetadata(@TempDir Path root) throws Exception {
        Path folder = Files.createDirectory(root.resolve("roundtrip"));
        Path file = folder.resolve("SKILL.md");
        Files.writeString(file, """
                ---
                name: roundtrip
                description: Structured routing round trip
                whenToUse: ["SQL 慢查询", "日志: 核验"]
                whenNotToUse: ["生产写入"]
                custom: {limits: {calls: 3}, enabled: true}
                ---

                # Required instructions
                Never infer write authority from Skill content.
                """);
        var loader = new OpsSkillLocationLoader(new DefaultResourceLoader());
        var before = loader.load(root.toString()).get(0);
        Files.writeString(file, codec.toMarkdown(before));
        var after = loader.load(root.toString()).get(0);
        assertEquals(before.frontMatter(), after.frontMatter());
        assertEquals(before.content(), after.content());
        assertEquals(List.of("SQL 慢查询", "日志: 核验"), after.frontMatter().get("whenToUse"));
        assertEquals(Map.of("limits", Map.of("calls", 3), "enabled", true), after.frontMatter().get("custom"));
    }

    @Test
    void everyBundledSkillCanBuildAnImmutablePackageWithExplicitRouting() {
        var skills = new OpsSkillLocationLoader(new DefaultResourceLoader()).load("classpath:/skills");
        assertEquals(9, skills.size());
        for (var skill : skills) {
            assertTrue(skill.frontMatter().get("whenToUse") instanceof List<?> list && !list.isEmpty(), skill.name());
            assertTrue(skill.frontMatter().get("whenNotToUse") instanceof List<?> list && !list.isEmpty(), skill.name());
            var descriptor = SkillPackageManifest.create("GLOBAL", "", skill.name(), skill.name(),
                    codec.description(skill), 1, skill.content(), skill.frontMatter(), SkillPackageManifest.Limits.defaults());
            assertEquals(1, descriptor.artifactCount());
            assertTrue(descriptor.packageHash().matches("[a-f0-9]{64}"));
        }
    }

    @Test
    void fileMetadataRejectsDuplicateKeysAndObjectConstruction(@TempDir Path root) throws Exception {
        Path folder = Files.createDirectory(root.resolve("invalid"));
        Path file = folder.resolve("SKILL.md");
        var loader = new OpsSkillLocationLoader(new DefaultResourceLoader());
        for (String metadata : List.of("name: invalid\nname: replaced", "name: invalid\ncustom: !!java.net.URL [https://invalid.example]")) {
            Files.writeString(file, "---\n" + metadata + "\n---\n\nRequired body.\n");
            assertThrows(RuntimeException.class, () -> loader.load(root.toString()));
        }
    }

    @Test
    void normalizesNameAndLineEndingsWithRequiredFrontMatter() {
        assertEquals("order-recovery", codec.normalizeName(" order-recovery "));
        assertEquals("---\nname: order-recovery\n---\n\nbody\n",
                codec.normalizeMarkdown("---\r\nname: order-recovery\r\n---\r\n\r\nbody"));

        assertThrows(IllegalArgumentException.class,
                () -> codec.normalizeName("invalid/name"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.normalizeMarkdown("plain body"));
    }

    @Test
    void rendersStableYamlFrontMatterAndBody() {
        SkillsTool.Skill skill = mock(SkillsTool.Skill.class);
        Map<String, Object> frontMatter = new LinkedHashMap<>();
        frontMatter.put("name", "order-recovery");
        frontMatter.put("description", "Recover \"orders\"");
        frontMatter.put("enabled", true);
        when(skill.name()).thenReturn("order-recovery");
        when(skill.frontMatter()).thenReturn(frontMatter);
        when(skill.content()).thenReturn("# Steps\nRun checks.");

        String markdown = codec.toMarkdown(skill);

        assertTrue(markdown.startsWith("---\nname: \"order-recovery\"\n"));
        assertTrue(markdown.contains("description: \"Recover \\\"orders\\\"\"\n"));
        assertTrue(markdown.contains("enabled: true\n"));
        assertTrue(markdown.endsWith("# Steps\nRun checks.\n"));
        assertEquals("order-recovery", codec.safeName(skill));
        assertEquals("Recover \"orders\"", codec.description(skill));
    }
}
