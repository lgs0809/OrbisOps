package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPackageBudget;
import cn.lgs.orbisops.domain.skill.model.SkillPackageSection;
import cn.lgs.orbisops.domain.skill.model.SkillPackageSectionType;
import cn.lgs.orbisops.domain.skill.model.SkillProgressiveLoadLevel;
import cn.lgs.orbisops.domain.skill.model.SkillProgressivePackage;
import cn.lgs.orbisops.domain.skill.service.SkillProgressivePackagePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProgressivePackagePolicyTest {

    private final SkillProgressivePackagePolicy policy =
            new SkillProgressivePackagePolicy();

    @Test
    void routingAndCoreLevelsMustLoadOnlyRequiredSections() {
        SkillProgressivePackage routing = policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.ROUTING_ONLY,
                sections(), new SkillPackageBudget(150, 0, 0, false));
        SkillProgressivePackage core = policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.CORE_EXECUTION,
                sections(), new SkillPackageBudget(350, 0, 0, false));

        assertEquals(List.of(SkillPackageSectionType.ROUTING_SUMMARY),
                routing.sections().stream().map(SkillPackageSection::type).toList());
        assertEquals(List.of(
                        SkillPackageSectionType.ROUTING_SUMMARY,
                        SkillPackageSectionType.CORE_PROCEDURE),
                core.sections().stream().map(SkillPackageSection::type).toList());
        assertFalse(routing.budgetExhausted());
        assertTrue(routing.packageHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void fullPackageMustRespectTokenModuleAndArtifactBudgets() {
        SkillProgressivePackage full = policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.FULL_EXECUTION,
                sections(), new SkillPackageBudget(450, 1, 0, false));

        assertEquals(List.of("routing", "core", "optional-a"),
                full.sections().stream().map(SkillPackageSection::sectionId).toList());
        assertEquals(450, full.totalTokens());
        assertTrue(full.budgetExhausted());
        assertFalse(full.sections().stream().anyMatch(section ->
                section.type() == SkillPackageSectionType.EVAL_SUITE));
    }

    @Test
    void evaluationSuiteMustRequireEvaluationMode() {
        SkillProgressivePackage nonEvaluation = policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.EVALUATION,
                sections(), new SkillPackageBudget(900, 2, 1, false));
        SkillProgressivePackage evaluation = policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.EVALUATION,
                sections(), new SkillPackageBudget(900, 2, 1, true));

        assertFalse(nonEvaluation.sections().stream().anyMatch(section ->
                section.type() == SkillPackageSectionType.EVAL_SUITE));
        assertTrue(evaluation.sections().stream().anyMatch(section ->
                section.type() == SkillPackageSectionType.EVAL_SUITE));
    }

    @Test
    void requiredSectionsMustNeverBeSilentlyTruncated() {
        assertThrows(IllegalStateException.class, () -> policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.CORE_EXECUTION,
                sections(), new SkillPackageBudget(250, 0, 0, false)));
        assertThrows(IllegalArgumentException.class, () -> policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.ROUTING_ONLY,
                List.of(section("core", SkillPackageSectionType.CORE_PROCEDURE,
                        200, 0, true)),
                new SkillPackageBudget(300, 0, 0, false)));
        assertThrows(IllegalArgumentException.class, () -> policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.ROUTING_ONLY,
                List.of(section("routing", SkillPackageSectionType.ROUTING_SUMMARY,
                        100, 100, false)),
                new SkillPackageBudget(300, 0, 0, false)));
        assertThrows(IllegalArgumentException.class, () -> policy.assemble(
                "skill-1", 7, SkillProgressiveLoadLevel.CORE_EXECUTION,
                List.of(
                        section("routing", SkillPackageSectionType.ROUTING_SUMMARY,
                                100, 100, true),
                        section("core", SkillPackageSectionType.CORE_PROCEDURE,
                                200, 100, false)),
                new SkillPackageBudget(300, 0, 0, false)));
    }

    private List<SkillPackageSection> sections() {
        return List.of(
                section("routing", SkillPackageSectionType.ROUTING_SUMMARY, 100, 100, true),
                section("core", SkillPackageSectionType.CORE_PROCEDURE, 200, 100, true),
                section("optional-a", SkillPackageSectionType.OPTIONAL_MODULE, 150, 20, false),
                section("optional-b", SkillPackageSectionType.OPTIONAL_MODULE, 100, 10, false),
                section("artifact-a", SkillPackageSectionType.ARTIFACT, 80, 10, false),
                section("eval-a", SkillPackageSectionType.EVAL_SUITE, 100, 10, false));
    }

    private SkillPackageSection section(
            String id,
            SkillPackageSectionType type,
            int tokens,
            int priority,
            boolean required) {
        return new SkillPackageSection(
                id, type, "content-" + id, tokens, priority, required);
    }
}
