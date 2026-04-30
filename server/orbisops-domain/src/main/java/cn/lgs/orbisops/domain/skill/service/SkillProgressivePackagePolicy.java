package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillPackageBudget;
import cn.lgs.orbisops.domain.skill.model.SkillPackageSection;
import cn.lgs.orbisops.domain.skill.model.SkillPackageSectionType;
import cn.lgs.orbisops.domain.skill.model.SkillProgressiveLoadLevel;
import cn.lgs.orbisops.domain.skill.model.SkillProgressivePackage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic, budget-aware Skill package projection. */
public final class SkillProgressivePackagePolicy {

    public SkillProgressivePackage assemble(
            String skillId,
            long skillVersion,
            SkillProgressiveLoadLevel level,
            List<SkillPackageSection> availableSections,
            SkillPackageBudget budget) {
        String id = required(skillId, "SKILL_PROGRESSIVE_PACKAGE_SKILL_ID_REQUIRED");
        if (skillVersion <= 0) {
            throw new IllegalArgumentException("SKILL_PROGRESSIVE_PACKAGE_VERSION_INVALID");
        }
        if (level == null) throw new IllegalArgumentException("SKILL_PROGRESSIVE_LOAD_LEVEL_REQUIRED");
        if (budget == null) throw new IllegalArgumentException("SKILL_PACKAGE_BUDGET_REQUIRED");
        List<SkillPackageSection> ordered = availableSections == null ? List.of()
                : availableSections.stream().sorted(sectionOrder()).toList();
        requireUniqueSectionIds(ordered);
        requireMandatorySections(level, ordered);

        List<SkillPackageSection> eligible = ordered.stream()
                .filter(section -> allowed(level, section.type(), budget.evaluationMode()))
                .toList();
        int requiredTokens = eligible.stream()
                .filter(SkillPackageSection::required)
                .mapToInt(SkillPackageSection::tokenEstimate)
                .sum();
        if (requiredTokens > budget.maxTokens()) {
            throw new IllegalStateException("SKILL_PACKAGE_REQUIRED_SECTION_BUDGET_EXCEEDED");
        }

        List<SkillPackageSection> selected = new ArrayList<>();
        int tokens = 0;
        int optionalModules = 0;
        int artifacts = 0;
        boolean budgetExhausted = false;
        for (SkillPackageSection section : eligible) {
            if (section.required()) {
                selected.add(section);
                tokens += section.tokenEstimate();
                continue;
            }
            if (section.type() == SkillPackageSectionType.OPTIONAL_MODULE
                    && optionalModules >= budget.maxOptionalModules()) {
                budgetExhausted = true;
                continue;
            }
            if (section.type() == SkillPackageSectionType.ARTIFACT
                    && artifacts >= budget.maxArtifacts()) {
                budgetExhausted = true;
                continue;
            }
            if (tokens + section.tokenEstimate() > budget.maxTokens()) {
                budgetExhausted = true;
                continue;
            }
            selected.add(section);
            tokens += section.tokenEstimate();
            if (section.type() == SkillPackageSectionType.OPTIONAL_MODULE) optionalModules++;
            if (section.type() == SkillPackageSectionType.ARTIFACT) artifacts++;
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("SKILL_PROGRESSIVE_PACKAGE_EMPTY");
        }

        Map<String, Object> hashInput = new LinkedHashMap<>();
        hashInput.put("skillId", id);
        hashInput.put("skillVersion", skillVersion);
        hashInput.put("loadLevel", level.name());
        hashInput.put("sections", selected.stream().map(section -> Map.of(
                "sectionId", section.sectionId(),
                "type", section.type().name(),
                "content", section.content(),
                "tokenEstimate", section.tokenEstimate(),
                "priority", section.priority(),
                "required", section.required())).toList());
        hashInput.put("totalTokens", tokens);
        return new SkillProgressivePackage(
                id, skillVersion, level, selected, tokens, budgetExhausted,
                CanonicalObjectHasher.sha256(hashInput));
    }

    private void requireMandatorySections(
            SkillProgressiveLoadLevel level,
            List<SkillPackageSection> sections) {
        List<SkillPackageSection> routing = sections.stream()
                .filter(section -> section.type() == SkillPackageSectionType.ROUTING_SUMMARY)
                .toList();
        if (routing.size() != 1 || !routing.get(0).required()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ROUTING_SUMMARY_REQUIRED");
        }
        if (level != SkillProgressiveLoadLevel.ROUTING_ONLY) {
            List<SkillPackageSection> core = sections.stream()
                    .filter(section -> section.type() == SkillPackageSectionType.CORE_PROCEDURE)
                    .toList();
            if (core.size() != 1 || !core.get(0).required()) {
                throw new IllegalArgumentException("SKILL_PACKAGE_CORE_PROCEDURE_REQUIRED");
            }
        }
    }

    private void requireUniqueSectionIds(List<SkillPackageSection> sections) {
        if (sections.stream().map(SkillPackageSection::sectionId).distinct().count()
                != sections.size()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_SECTION_DUPLICATE");
        }
    }

    private boolean allowed(
            SkillProgressiveLoadLevel level,
            SkillPackageSectionType type,
            boolean evaluationMode) {
        return switch (level) {
            case ROUTING_ONLY -> type == SkillPackageSectionType.ROUTING_SUMMARY;
            case CORE_EXECUTION -> type == SkillPackageSectionType.ROUTING_SUMMARY
                    || type == SkillPackageSectionType.CORE_PROCEDURE;
            case FULL_EXECUTION -> type != SkillPackageSectionType.EVAL_SUITE;
            case EVALUATION -> type != SkillPackageSectionType.EVAL_SUITE || evaluationMode;
        };
    }

    private Comparator<SkillPackageSection> sectionOrder() {
        return Comparator
                .comparingInt((SkillPackageSection section) -> typeOrder(section.type()))
                .thenComparing(Comparator.comparingInt(SkillPackageSection::priority).reversed())
                .thenComparing(SkillPackageSection::sectionId);
    }

    private int typeOrder(SkillPackageSectionType type) {
        return switch (type) {
            case ROUTING_SUMMARY -> 0;
            case CORE_PROCEDURE -> 1;
            case OPTIONAL_MODULE -> 2;
            case ARTIFACT -> 3;
            case EVAL_SUITE -> 4;
        };
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
