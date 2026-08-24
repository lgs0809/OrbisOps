package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceEvidenceReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionEvalCaseFactoryTest {

    @Test
    void derivesPositiveNegativeAndHardCaseRegressionFixtures() {
        Map<String, Object> authored = Map.of(
                "changes",
                List.of(Map.of(
                        "section", "routingProfile",
                        "operation", "upsert",
                        "key", "routing",
                        "value", Map.of(
                                "category", "OBSERVABILITY",
                                "subcategory", "errors",
                                "whenToUse",
                                List.of("排查接口错误"),
                                "whenNotToUse",
                                List.of("生成演示文稿"),
                                "keywords",
                                List.of("错误日志")))));
        SkillExperienceConsolidationSample hardCase =
                new SkillExperienceConsolidationSample(
                        "obs-1",
                        "run-1",
                        "session-1",
                        "ROUTING_CORRECTION",
                        "INCOMPLETE",
                        "task-hash",
                        "trajectory-hash",
                        "不能只查询 health 后就下结论",
                        0.8D,
                        List.of(new SkillExperienceEvidenceReference(
                                "e1",
                                "r1",
                                "h1",
                                "TOOL")));

        Map<String, Object> result =
                new SkillEvolutionEvalCaseFactory().enrich(
                        authored,
                        List.of(hardCase));
        List<?> cases = (List<?>) result.get("evalCases");
        List<?> artifacts = (List<?>) result.get("artifacts");

        assertEquals(3, cases.size());
        cases.forEach(c -> assertEquals("DEVELOPMENT_ONLY",((Map<?,?>)c).get("usage")));
        assertTrue(String.valueOf(cases).contains("ROUTING_POSITIVE"));
        assertTrue(String.valueOf(cases).contains("ROUTING_NEGATIVE"));
        assertTrue(String.valueOf(cases).contains("HARD_CASE"));
        assertTrue(String.valueOf(artifacts).contains(
                "evals/routing-boundary-cases.json"));
    }
}
