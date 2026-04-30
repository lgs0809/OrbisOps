package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeRecallPolicyTest {

    private final SkillRuntimeRecallPolicy policy = new SkillRuntimeRecallPolicy();

    @Test
    void recallsRelevantSkillBeyondOriginalCatalogOrder() {
        List<SkillRuntimeCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 150; index++) {
            candidates.add(skill(
                    "code-" + index,
                    "Java 代码生成和代码审查",
                    new SkillRoutingProfile("DEVELOPMENT", "", "Java 代码生成",
                            List.of("开发后端服务"), List.of("制作 PPT"), List.of("Java"))));
        }
        candidates.add(skill(
                "quarterly-ppt",
                "季度汇报 PPT",
                new SkillRoutingProfile("DOCUMENT", "PRESENTATION", "季度汇报 PPT",
                        List.of("季度总结", "融资路演"), List.of("后端接口开发"), List.of("PPT"))));

        List<SkillRuntimeCandidate> recalled = policy.recall(
                "帮我生成季度汇报的 PPT", candidates, Set.of(), 12);

        assertEquals(12, recalled.size());
        assertEquals("quarterly-ppt", recalled.get(0).skillId());
    }

    @Test
    void alwaysKeepsExplicitActiveSkillInsideBoundedRecall() {
        List<SkillRuntimeCandidate> candidates = List.of(
                skill("logs", "日志查询", SkillRoutingProfile.empty("日志查询")),
                skill("manual", "人工指定", SkillRoutingProfile.empty("无匹配描述")));

        List<SkillRuntimeCandidate> recalled = policy.recall(
                "查询日志", candidates, Set.of("manual"), 1);

        assertEquals(1, recalled.size());
        assertEquals("manual", recalled.get(0).skillId());
    }

    private SkillRuntimeCandidate skill(String id,
                                        String description,
                                        SkillRoutingProfile profile) {
        return new SkillRuntimeCandidate(
                id, "demo-project", "PROJECT", id, description, 1,
                "skill-hash-" + id, "package-hash-" + id, "manifest-hash-" + id,
                Map.of("SKILL.md", "artifact-" + id), "SKILL.md",
                "ACTIVE", "AUTO", description.length(), profile);
    }
}
