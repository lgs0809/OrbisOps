package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillMarkdownCodecTest {

    private final OpsSkillMarkdownCodec codec = new OpsSkillMarkdownCodec();

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
