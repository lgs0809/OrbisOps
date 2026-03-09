package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillReleaseService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Materializes frozen Skill catalog references and version-bound runtime context. */
public final class OpsRuntimeFrozenSkillContextResolver {

    private final Supplier<SkillCatalogQueryService> catalogQuerySupplier;
    private final Supplier<OpsSkillReleaseService> releaseServiceSupplier;
    private final OpsRuntimeSkillSettings settings;

    public OpsRuntimeFrozenSkillContextResolver(
            Supplier<SkillCatalogQueryService> catalogQuerySupplier,
            Supplier<OpsSkillReleaseService> releaseServiceSupplier,
            OpsRuntimeSkillSettings settings) {
        if (catalogQuerySupplier == null) {
            throw new IllegalArgumentException("SKILL_CATALOG_QUERY_SUPPLIER_REQUIRED");
        }
        if (releaseServiceSupplier == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_SERVICE_SUPPLIER_REQUIRED");
        }
        if (settings == null) {
            throw new IllegalArgumentException("RUNTIME_SKILL_SETTINGS_REQUIRED");
        }
        this.catalogQuerySupplier = catalogQuerySupplier;
        this.releaseServiceSupplier = releaseServiceSupplier;
        this.settings = settings;
    }

    @SuppressWarnings("unchecked")
    public String render(OpsRuntimeResourceContext context) {
        SkillCatalogQueryService catalogQuery = catalogQuerySupplier.get();
        if (catalogQuery == null || context == null || context.getRequest() == null
                || context.getRequest().getMetadata() == null) {
            return "";
        }
        int totalBudget = Math.max(1000, settings.contextMaxChars());
        StringBuilder builder = new StringBuilder();
        appendCatalogSummary(context, builder);
        Object refsValue = context.getRequest().getMetadata().get("usedSkillVersionRefs");
        if (!(refsValue instanceof Iterable<?> iterable)) {
            return builder.toString();
        }
        builder.append("\n### 已绑定到本次 Work Session 的 Skill 版本\n");
        int count = 0;
        List<Map<String, Object>> frozenCanaryRefs = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String status = firstText(String.valueOf(map.get("statusAtUse")));
            if ("CANARY".equalsIgnoreCase(status)) {
                Map<String, Object> frozenRef = new LinkedHashMap<>();
                map.forEach((key, value) -> frozenRef.put(String.valueOf(key), value));
                frozenCanaryRefs.add(Map.copyOf(frozenRef));
                continue;
            }
            String skillId = firstText(String.valueOf(map.get("skillId")));
            int version = intValue(map.get("version"), 0);
            String skillHash = firstText(String.valueOf(map.get("skillHash")));
            String packageHash = firstText(String.valueOf(map.get("packageHash")));
            String scope = firstText(String.valueOf(map.get("scope")));
            if (!StringUtils.hasText(skillId) || version <= 0
                    || !StringUtils.hasText(skillHash)
                    || !StringUtils.hasText(packageHash)) {
                throw new IllegalStateException(
                        "Runtime Context Bundle 中的 Skill 版本引用不完整");
            }
            Map<String, Object> skill = catalogQuery.getSkillVersion(
                    context.getProjectId(), skillId, version, skillHash, packageHash, scope);
            String heading = "\n#### " + skill.getOrDefault("name", skillId) + "\n";
            int remaining = totalBudget - builder.length() - heading.length();
            if (remaining < 120) {
                break;
            }
            String content = String.valueOf(skill.getOrDefault("content", ""));
            int perSkillBudget = Math.min(
                    Math.max(200, settings.maxSingleSkillChars()), remaining);
            builder.append(heading)
                    .append(abbreviate(content, perSkillBudget))
                    .append('\n');
            count++;
        }
        if (!frozenCanaryRefs.isEmpty()) {
            OpsSkillReleaseService releaseService = releaseServiceSupplier.get();
            if (releaseService == null) {
                throw new IllegalStateException(
                        "Runtime Context Bundle 包含 Canary Skill，但 Skill Release 服务不可用");
            }
            String canary = releaseService.renderFrozenCanaryContext(
                    context.getProjectId(),
                    context.getDefinition() == null
                            ? ""
                            : context.getDefinition().getAgentId(),
                    frozenCanaryRefs);
            int remaining = totalBudget - builder.length();
            if (StringUtils.hasText(canary) && remaining >= 120) {
                builder.append("\n").append(abbreviate(canary, remaining));
                count++;
            }
        }
        return count == 0 && builder.length() == 0
                ? ""
                : abbreviate(builder.toString(), totalBudget);
    }

    public List<Map<String, Object>> catalogRefs(OpsRuntimeResourceContext context) {
        if (context == null || context.getRequest() == null
                || context.getRequest().getMetadata() == null) {
            return List.of();
        }
        Object value = context.getRequest().getMetadata().get("skillCatalogRefs");
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> ref.put(String.valueOf(key), itemValue));
            refs.add(ref);
        }
        return List.copyOf(refs);
    }

    private void appendCatalogSummary(
            OpsRuntimeResourceContext context,
            StringBuilder builder) {
        Object catalogValue = context.getRequest().getMetadata().get("skillCatalogRefs");
        if (!(catalogValue instanceof Iterable<?> catalog)) {
            return;
        }
        StringBuilder catalogSummary = new StringBuilder(
                "### 本次 Work Session 的 Skill 轻量目录\n");
        for (Object item : catalog) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String skillId = firstText(String.valueOf(map.get("skillId")));
            String name = firstText(String.valueOf(map.get("name")), skillId);
            String description = firstText(String.valueOf(map.get("description")));
            String category = firstText(String.valueOf(map.get("category")));
            String exclusion = firstListValue(map.get("whenNotToUse"));
            String line = "- "
                    + (StringUtils.hasText(category) ? "[" + category + "] " : "")
                    + name + " (`" + skillId + "`)"
                    + (StringUtils.hasText(description) ? "：" + description : "")
                    + (StringUtils.hasText(exclusion) ? "；不适用：" + exclusion : "")
                    + "\n";
            if (catalogSummary.length() + line.length()
                    > Math.max(300, settings.catalogSummaryMaxChars())) {
                break;
            }
            catalogSummary.append(line);
        }
        if (catalogSummary.length()
                > "### 本次 Work Session 的 Skill 轻量目录\n".length()) {
            builder.append(catalogSummary)
                    .append("目录只用于选择规程；以下只加载与当前问题相关且已绑定版本/hash 的少量正文。\n");
        }
    }

    private String abbreviate(String value, int maxChars) {
        String safe = value == null ? "" : value;
        int limit = Math.max(1, maxChars);
        return safe.length() <= limit
                ? safe
                : safe.substring(0, limit)
                + "\n[Skill 内容已按上下文预算截断，可通过 Skill 目录工具读取绑定版本。]";
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String firstListValue(Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = firstText(String.valueOf(item));
                if (StringUtils.hasText(text)) return text;
            }
        }
        return "";
    }
}
