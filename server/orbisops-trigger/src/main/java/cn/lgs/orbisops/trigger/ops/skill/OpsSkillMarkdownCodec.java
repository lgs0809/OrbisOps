package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEditableWorkspacePort;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SKILL.md validation, normalization, front matter, and SDK name projection. */
final class OpsSkillMarkdownCodec {

    SkillsTool.Skill validate(
            String expectedName,
            String markdown,
            SkillEditableWorkspacePort workspacePort,
            OpsSkillLocationLoader locationLoader) throws IOException {
        String tempRoot = workspacePort.createValidationWorkspace(
                expectedName,
                markdown);
        try {
            List<SkillsTool.Skill> skills = locationLoader.load(tempRoot);
            SkillsTool.Skill skill = skills.stream()
                    .filter(item -> expectedName.equals(safeName(item)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SKILL.md 必须包含 front matter，并且 name 必须等于 " + expectedName));
            if (!StringUtils.hasText(skill.content())) {
                throw new IllegalArgumentException("SKILL.md 正文不能为空。");
            }
            return skill;
        } finally {
            workspacePort.deleteWorkspace(tempRoot);
        }
    }

    String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Skill 名称不能为空。");
        }
        String trimmed = name.trim();
        if (!trimmed.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Skill 名称只允许字母、数字、点、下划线和中划线。");
        }
        return trimmed;
    }

    String normalizeMarkdown(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalArgumentException("SKILL.md 内容不能为空。");
        }
        String normalized = markdown
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("SKILL.md 必须以 YAML front matter 开头。");
        }
        return normalized + "\n";
    }

    String toMarkdown(SkillsTool.Skill skill) {
        if (skill == null) {
            return "";
        }
        StringBuilder markdown = new StringBuilder();
        markdown.append("---\n");
        Map<String, Object> frontMatter = Optional.ofNullable(skill.frontMatter())
                .orElse(Map.of());
        appendFrontMatterValue(
                markdown,
                "name",
                frontMatter.getOrDefault("name", skill.name()));
        appendFrontMatterValue(
                markdown,
                "description",
                frontMatter.get("description"));
        frontMatter.forEach((key, value) -> {
            if (!"name".equals(key) && !"description".equals(key)) {
                appendFrontMatterValue(markdown, key, value);
            }
        });
        markdown.append("---\n\n");
        markdown.append(Optional.ofNullable(skill.content()).orElse("").strip());
        markdown.append('\n');
        return markdown.toString();
    }

    String safeName(SkillsTool.Skill skill) {
        if (skill == null || skill.frontMatter() == null) {
            return "";
        }
        return stringValue(skill.frontMatter().get("name"));
    }

    String description(SkillsTool.Skill skill) {
        return skill == null || skill.frontMatter() == null
                ? ""
                : stringValue(skill.frontMatter().get("description"));
    }

    private void appendFrontMatterValue(
            StringBuilder markdown,
            String key,
            Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        markdown.append(key.matches("[A-Za-z_][A-Za-z0-9_-]*") ? key : CanonicalJson.stringify(key))
                .append(": ")
                .append(yamlScalar(value))
                .append('\n');
    }

    private String yamlScalar(Object value) {
        // JSON flow collections are valid YAML. Quoting their Java toString() loses the schema.
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return CanonicalJson.stringify(value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return CanonicalJson.stringify(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
