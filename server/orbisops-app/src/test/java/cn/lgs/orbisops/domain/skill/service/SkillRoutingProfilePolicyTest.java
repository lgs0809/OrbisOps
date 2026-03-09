package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillRoutingProfilePolicyTest {

    private final SkillRoutingProfilePolicy policy = new SkillRoutingProfilePolicy();

    @Test
    void extractsRoutingSectionsWithoutTreatingExclusionsAsPositiveText() {
        SkillRoutingProfile profile = policy.profile(
                "", "", "PPT Generator",
                "制作季度汇报和路演演示文稿。不支持后端接口开发",
                """
                        ---
                        keywords: [PPT, 季度总结, 路演]
                        ---
                        # When To Use
                        - 用户需要可编辑的演示文稿
                        # When Not To Use
                        - 数据库迁移
                        - Java 后端开发
                        """,
                List.of(), List.of(), List.of());

        assertEquals("DOCUMENT", profile.category());
        assertTrue(profile.useCases().contains("用户需要可编辑的演示文稿"));
        assertTrue(profile.exclusions().contains("数据库迁移"));
        assertTrue(profile.exclusions().stream().anyMatch(value -> value.contains("后端接口开发")));
        assertTrue(profile.keywords().contains("PPT"));
        assertFalse(profile.positiveText().contains("后端接口开发"));
        assertTrue(profile.negativeText().contains("后端接口开发"));
    }

    @Test
    void respectsExplicitRoutingMetadata() {
        SkillRoutingProfile profile = policy.profile(
                "FINANCE", "REPORTING", "Quarterly Pack", "生成财务材料", "",
                List.of("季度结账"), List.of("代码生成"), List.of("利润表"));

        assertEquals("FINANCE", profile.category());
        assertEquals("REPORTING", profile.subcategory());
        assertTrue(profile.useCases().contains("季度结账"));
        assertTrue(profile.exclusions().contains("代码生成"));
    }

    @Test
    void newRoutingProfileRequiresBothPositiveAndNegativeBoundaries() {
        IllegalArgumentException missingNegative = assertThrows(
                IllegalArgumentException.class,
                () -> policy.requireProfile(
                        "DOCUMENT",
                        "",
                        "PPT",
                        "",
                        "",
                        List.of("制作季度汇报"),
                        List.of(),
                        List.of("PPT")));

        assertTrue(missingNegative.getMessage().contains(
                "whenNotToUse"));
        assertEquals(
                8,
                policy.supportedCategories().size());
    }

    @Test
    void runtimeCanonicalProfileDoesNotRecoverMissingFieldsFromLegacyMarkdown() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.requireCanonicalProfile(
                        "DOCUMENT",
                        "",
                        "Legacy PPT",
                        "旧目录记录",
                        List.of(),
                        List.of(),
                        List.of("PPT")));

        assertEquals(
                "SKILL_ROUTING_PROFILE_REQUIRED:whenToUse,whenNotToUse",
                error.getMessage());
    }
}
