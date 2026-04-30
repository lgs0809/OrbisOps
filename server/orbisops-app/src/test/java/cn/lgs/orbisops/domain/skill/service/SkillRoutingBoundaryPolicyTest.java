package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRoutingBoundaryPolicyTest {

    private final SkillRoutingBoundaryPolicy policy =
            new SkillRoutingBoundaryPolicy();

    @Test
    void positiveAndNegativeScenariosUseTheSameBoundarySemantics() {
        SkillRoutingProfile profile = new SkillRoutingProfile(
                "DOCUMENT",
                "presentation",
                "生成可编辑演示文稿",
                List.of("制作季度汇报 PPT", "生成产品演示文稿"),
                List.of("后端接口开发", "数据库迁移"),
                List.of("PPT", "汇报"));

        assertTrue(policy.match("帮我制作季度汇报 PPT", profile).matched());
        assertFalse(policy.match("帮我开发后端接口", profile).matched());
    }

    @Test
    void rejectsOverlappingOrUniversalRoutingBoundaries() {
        SkillRoutingProfile overlapping = new SkillRoutingProfile(
                "GENERAL",
                "",
                "",
                List.of("任何任务都可以使用", "生成季度汇报"),
                List.of("不要用于生成季度汇报"),
                List.of());

        List<String> failures = policy.validate(overlapping);

        assertTrue(failures.contains(
                "SKILL_ROUTING_WHEN_TO_USE_TOO_BROAD"));
        assertTrue(failures.contains(
                "SKILL_ROUTING_BOUNDARY_OVERLAP"));
    }
}
