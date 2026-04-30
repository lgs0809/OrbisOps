package cn.lgs.orbisops.trigger.ops.skill;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Required-skill filtering and bounded full/summary context rendering. */
final class OpsSkillContextRenderer {

    private final OpsSkillMarkdownCodec markdownCodec;

    OpsSkillContextRenderer(OpsSkillMarkdownCodec markdownCodec) {
        this.markdownCodec = markdownCodec;
    }

    List<OpsSkillToolProvider.SkillSummary> summaries(
            List<SkillsTool.Skill> skills) {
        return skills.stream()
                .map(skill -> new OpsSkillToolProvider.SkillSummary(
                        skill.name(),
                        markdownCodec.description(skill),
                        skill.basePath()))
                .toList();
    }

    List<SkillsTool.Skill> filterRequired(
            List<SkillsTool.Skill> skills,
            Collection<String> requiredSkillNames) {
        if (requiredSkillNames == null || requiredSkillNames.isEmpty()) {
            return List.of();
        }
        Set<String> required = requiredSkillNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
        if (required.isEmpty()) {
            return List.of();
        }
        return skills.stream()
                .filter(skill -> required.contains(markdownCodec.safeName(skill)))
                .toList();
    }

    String renderFull(List<SkillsTool.Skill> skills, int maxChars) {
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (SkillsTool.Skill skill : skills) {
            context.append("## Skill: ").append(skill.name()).append('\n');
            context.append("- description: ")
                    .append(markdownCodec.description(skill))
                    .append('\n');
            context.append("- basePath: ")
                    .append(skill.basePath())
                    .append("\n\n");
            context.append(skill.content()).append("\n\n");
            if (context.length() >= maxChars) {
                return context.substring(0, maxChars)
                        + "\n... skill context truncated ...";
            }
        }
        return context.toString();
    }

    String renderSummary(List<SkillsTool.Skill> skills, int maxChars) {
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (SkillsTool.Skill skill : skills) {
            context.append("- ")
                    .append(skill.name())
                    .append(": ")
                    .append(markdownCodec.description(skill))
                    .append('\n');
            if (context.length() >= maxChars) {
                return context.substring(0, maxChars)
                        + "\n... skill summary truncated ...";
            }
        }
        return context.toString();
    }
}
