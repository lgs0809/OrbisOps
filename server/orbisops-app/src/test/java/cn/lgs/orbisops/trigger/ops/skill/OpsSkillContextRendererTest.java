package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillContextRendererTest {

    private final OpsSkillContextRenderer renderer =
            new OpsSkillContextRenderer(new OpsSkillMarkdownCodec());

    @Test
    void filtersRequiredSkillsByFrontMatterNameAndProjectsSummary() {
        SkillsTool.Skill order = skill(
                "order-recovery",
                "Recover failed orders",
                "/skills/order",
                "Order body");
        SkillsTool.Skill redis = skill(
                "redis-capacity",
                "Inspect Redis capacity",
                "/skills/redis",
                "Redis body");

        List<SkillsTool.Skill> selected = renderer.filterRequired(
                List.of(order, redis),
                List.of(" redis-capacity ", "missing"));

        assertEquals(1, selected.size());
        assertEquals("redis-capacity", selected.get(0).name());
        List<OpsSkillToolProvider.SkillSummary> summaries =
                renderer.summaries(selected);
        assertEquals("Inspect Redis capacity", summaries.get(0).description());
        assertEquals("/skills/redis", summaries.get(0).basePath());
    }

    @Test
    void rendersFullAndSummaryContextsWithStableTruncationMarkers() {
        SkillsTool.Skill skill = skill(
                "order-recovery",
                "Recover failed orders",
                "/skills/order",
                "A".repeat(200));

        String full = renderer.renderFull(List.of(skill), 80);
        String summary = renderer.renderSummary(List.of(skill), 20);

        assertTrue(full.endsWith("\n... skill context truncated ..."));
        assertTrue(summary.endsWith("\n... skill summary truncated ..."));
        assertFalse(renderer.renderFull(List.of(), 100).length() > 0);
    }

    private SkillsTool.Skill skill(
            String name,
            String description,
            String basePath,
            String content) {
        SkillsTool.Skill skill = mock(SkillsTool.Skill.class);
        when(skill.name()).thenReturn(name);
        when(skill.frontMatter()).thenReturn(Map.of(
                "name", name,
                "description", description));
        when(skill.basePath()).thenReturn(basePath);
        when(skill.content()).thenReturn(content);
        return skill;
    }
}
