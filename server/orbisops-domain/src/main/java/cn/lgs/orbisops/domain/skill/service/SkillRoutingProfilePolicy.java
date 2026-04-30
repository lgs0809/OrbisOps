package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Extracts retrieval metadata from a Skill summary and its optional SKILL.md routing sections. */
public final class SkillRoutingProfilePolicy {

    private static final Map<String, List<String>> CATEGORY_TERMS = categories();
    private static final Set<String> CATEGORIES = Set.of(
            "DOCUMENT",
            "DEVELOPMENT",
            "DATA",
            "OBSERVABILITY",
            "OPERATIONS",
            "COMMUNICATION",
            "KNOWLEDGE",
            "GENERAL");
    private static final Set<String> NEGATIVE_MARKERS = Set.of(
            "when not to use", "not for", "unsupported", "不适用", "不支持", "不要用于", "禁止用于", "不能用于");

    public SkillRoutingProfile profile(String category,
                                       String subcategory,
                                       String name,
                                       String description,
                                       String content,
                                       Object useCases,
                                       Object exclusions,
                                       Object keywords) {
        ParsedSections parsed = parse(content);
        List<String> positive = merge(values(useCases), parsed.useCases());
        List<String> negative = merge(values(exclusions), parsed.exclusions(), negativeSentences(description));
        List<String> searchKeywords = merge(values(keywords), parsed.keywords());
        String searchDescription = removeNegativeSentences(description);
        String inferredCategory = text(category);
        if (inferredCategory.isBlank()) {
            inferredCategory = category(String.join(" ", name, searchDescription,
                    String.join(" ", positive), String.join(" ", searchKeywords)));
        }
        return new SkillRoutingProfile(
                inferredCategory,
                text(subcategory),
                searchDescription,
                positive,
                negative,
                searchKeywords);
    }

    /**
     * New Skill packages must provide an explicit positive and negative routing
     * boundary. Category may still be inferred because it is an index partition,
     * not user authorization or execution policy.
     */
    public SkillRoutingProfile requireProfile(String category,
                                              String subcategory,
                                              String name,
                                              String description,
                                              String content,
                                              Object whenToUse,
                                              Object whenNotToUse,
                                              Object keywords) {
        SkillRoutingProfile profile = profile(
                category,
                subcategory,
                name,
                description,
                content,
                whenToUse,
                whenNotToUse,
                keywords);
        List<String> missing = new ArrayList<>();
        if (profile.useCases().isEmpty()) missing.add("whenToUse");
        if (profile.exclusions().isEmpty()) missing.add("whenNotToUse");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "SKILL_ROUTING_PROFILE_REQUIRED:" + String.join(",", missing));
        }
        if (!CATEGORIES.contains(profile.category())) {
            throw new IllegalArgumentException(
                    "SKILL_ROUTING_CATEGORY_INVALID:" + profile.category());
        }
        return profile;
    }

    /** Runtime catalog entries must expose the canonical V3 routing fields. */
    public SkillRoutingProfile requireCanonicalProfile(String category,
                                                       String subcategory,
                                                       String name,
                                                       String description,
                                                       Object whenToUse,
                                                       Object whenNotToUse,
                                                       Object keywords) {
        List<String> positive = values(whenToUse);
        List<String> negative = values(whenNotToUse);
        List<String> missing = new ArrayList<>();
        if (positive.isEmpty()) missing.add("whenToUse");
        if (negative.isEmpty()) missing.add("whenNotToUse");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "SKILL_ROUTING_PROFILE_REQUIRED:" + String.join(",", missing));
        }
        return requireProfile(
                category,
                subcategory,
                name,
                description,
                "",
                positive,
                negative,
                keywords);
    }

    public Map<String, Object> toMap(SkillRoutingProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("SKILL_ROUTING_PROFILE_REQUIRED");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", profile.category());
        result.put("subcategory", profile.subcategory());
        result.put("whenToUse", profile.useCases());
        result.put("whenNotToUse", profile.exclusions());
        result.put("keywords", profile.keywords());
        return Map.copyOf(result);
    }

    public List<String> supportedCategories() {
        return List.of(
                "DOCUMENT",
                "DEVELOPMENT",
                "DATA",
                "OBSERVABILITY",
                "OPERATIONS",
                "COMMUNICATION",
                "KNOWLEDGE",
                "GENERAL");
    }

    public String category(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return "GENERAL";
        String selected = "GENERAL";
        int best = 0;
        for (Map.Entry<String, List<String>> entry : CATEGORY_TERMS.entrySet()) {
            int score = 0;
            for (String term : entry.getValue()) {
                if (normalized.contains(term)) score++;
            }
            if (score > best) {
                best = score;
                selected = entry.getKey();
            }
        }
        return selected;
    }

    private ParsedSections parse(String content) {
        if (content == null || content.isBlank()) return ParsedSections.empty();
        List<String> useCases = new ArrayList<>();
        List<String> exclusions = new ArrayList<>();
        List<String> keywords = new ArrayList<>();
        Section section = Section.NONE;
        boolean frontMatter = false;
        int lineIndex = 0;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (lineIndex++ == 0 && "---".equals(line)) {
                frontMatter = true;
                continue;
            }
            if (frontMatter) {
                if ("---".equals(line)) {
                    frontMatter = false;
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = normalize(line.substring(0, colon));
                    String value = line.substring(colon + 1).trim();
                    if (positiveHeading(key)) useCases.addAll(values(value));
                    else if (negativeHeading(key)) exclusions.addAll(values(value));
                    else if (keywordHeading(key)) keywords.addAll(values(value));
                }
                continue;
            }
            if (line.startsWith("#")) {
                String heading = normalize(line.replaceFirst("^#+", ""));
                section = positiveHeading(heading) ? Section.POSITIVE
                        : negativeHeading(heading) ? Section.NEGATIVE
                        : keywordHeading(heading) ? Section.KEYWORDS : Section.NONE;
                continue;
            }
            String item = line.replaceFirst("^[-*+]\\s+", "").trim();
            if (item.isBlank()) continue;
            if (section == Section.POSITIVE) useCases.add(item);
            else if (section == Section.NEGATIVE) exclusions.add(item);
            else if (section == Section.KEYWORDS) keywords.addAll(values(item));
        }
        return new ParsedSections(distinct(useCases), distinct(exclusions), distinct(keywords));
    }

    private List<String> negativeSentences(String description) {
        List<String> result = new ArrayList<>();
        for (String sentence : sentences(description)) {
            if (negative(sentence)) result.add(sentence.trim());
        }
        return distinct(result);
    }

    private String removeNegativeSentences(String description) {
        List<String> positive = sentences(description).stream()
                .filter(sentence -> !negative(sentence))
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .toList();
        return positive.isEmpty() ? text(description) : String.join("。", positive);
    }

    private List<String> sentences(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split("[。；;\\n]+"));
    }

    private boolean negative(String value) {
        String normalized = normalize(value);
        return NEGATIVE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean positiveHeading(String value) {
        String normalized = normalize(value).replace("_", " ");
        return normalized.contains("when to use")
                || normalized.contains("use cases")
                || normalized.contains("触发场景")
                || normalized.contains("适用场景")
                || normalized.contains("使用场景");
    }

    private boolean negativeHeading(String value) {
        String normalized = normalize(value).replace("_", " ");
        return NEGATIVE_MARKERS.stream().anyMatch(normalized::contains)
                || normalized.contains("禁用场景");
    }

    private boolean keywordHeading(String value) {
        String normalized = normalize(value);
        return normalized.contains("keyword")
                || normalized.contains("关键词")
                || normalized.contains("触发词");
    }

    private List<String> values(Object input) {
        if (input == null) return List.of();
        if (input instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            iterable.forEach(item -> result.addAll(values(item)));
            return distinct(result);
        }
        String value = text(input);
        if (value.isBlank()) return List.of();
        String normalized = value.replace("[", "").replace("]", "").replace("\"", "");
        List<String> result = new ArrayList<>();
        for (String item : normalized.split("[,，|]")) {
            if (!item.trim().isBlank()) result.add(item.trim());
        }
        return distinct(result);
    }

    @SafeVarargs
    private final List<String> merge(List<String>... sources) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (List<String> source : sources) {
            if (source != null) result.addAll(source);
        }
        return result.stream().filter(value -> !value.isBlank()).limit(32).toList();
    }

    private List<String> distinct(List<String> source) {
        return source == null ? List.of() : source.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(32)
                .toList();
    }

    private String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Map<String, List<String>> categories() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("DOCUMENT", List.of(
                "ppt", "powerpoint", "幻灯片", "演示文稿", "季度汇报", "汇报", "路演", "文档", "pdf"));
        result.put("DEVELOPMENT", List.of(
                "代码", "编程", "开发", "java", "python", "前端", "后端", "接口", "bug", "测试"));
        result.put("DATA", List.of(
                "sql", "mysql", "postgres", "数据库", "数据表", "查询", "索引", "慢查询"));
        result.put("OBSERVABILITY", List.of(
                "日志", "指标", "trace", "prometheus", "elasticsearch", "elk", "链路", "告警"));
        result.put("OPERATIONS", List.of(
                "运维", "docker", "kubernetes", "k8s", "部署", "发布", "配置", "重启", "巡检"));
        result.put("COMMUNICATION", List.of(
                "通知", "消息", "channel", "邮件", "email", "webhook", "slack", "企业微信"));
        result.put("KNOWLEDGE", List.of(
                "知识库", "rag", "检索文档", "问答", "资料"));
        return Map.copyOf(result);
    }

    private enum Section { NONE, POSITIVE, NEGATIVE, KEYWORDS }

    private record ParsedSections(List<String> useCases,
                                  List<String> exclusions,
                                  List<String> keywords) {
        private static ParsedSections empty() {
            return new ParsedSections(List.of(), List.of(), List.of());
        }
    }
}
