package cn.lgs.orbisops.application.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Transport-neutral snapshot of a file-backed Skill supplied by an outer adapter. */
public record SkillFileDefinition(String name,
                                  String basePath,
                                  Map<String, Object> frontMatter,
                                  String content,
                                  String markdown,
                                  String xml) {

    public SkillFileDefinition {
        name = text(name);
        basePath = text(basePath);
        frontMatter = frontMatter == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(frontMatter));
        content = raw(content);
        markdown = raw(markdown);
        xml = raw(xml);
        if (name.isBlank()) throw new IllegalArgumentException("SKILL_FILE_NAME_REQUIRED");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String raw(String value) {
        return value == null ? "" : value;
    }
}
