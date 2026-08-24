package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort;
import cn.lgs.orbisops.domain.skill.service.SkillRuntimeBodyPolicy;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillReleaseService;
import java.util.*;
import java.util.function.Supplier;

/** Resolves exact governed versions and whole required sections; never truncates Skill instructions. */
public final class OpsRuntimeFrozenSkillContextResolver {
    private final Supplier<SkillCatalogQueryService> catalogQuerySupplier;
    private final Supplier<OpsSkillReleaseService> releaseServiceSupplier;
    private final OpsRuntimeSkillSettings settings;
    private final SkillRuntimeBudgetPort budget;
    private final SkillRuntimeBodyPolicy bodyPolicy = new SkillRuntimeBodyPolicy();
    public OpsRuntimeFrozenSkillContextResolver(Supplier<SkillCatalogQueryService> catalogQuerySupplier,
            Supplier<OpsSkillReleaseService> releaseServiceSupplier, OpsRuntimeSkillSettings settings) {
        this(catalogQuerySupplier, releaseServiceSupplier, settings, (project, run, loads) -> {
            if (!loads.isEmpty()) throw new IllegalStateException("SKILL_RUNTIME_BUDGET_STORE_REQUIRED");
        });
    }
    public OpsRuntimeFrozenSkillContextResolver(Supplier<SkillCatalogQueryService> catalogQuerySupplier,
            Supplier<OpsSkillReleaseService> releaseServiceSupplier, OpsRuntimeSkillSettings settings, SkillRuntimeBudgetPort budget) {
        this.catalogQuerySupplier = Objects.requireNonNull(catalogQuerySupplier, "SKILL_CATALOG_QUERY_SUPPLIER_REQUIRED");
        this.releaseServiceSupplier = Objects.requireNonNull(releaseServiceSupplier, "SKILL_RELEASE_SERVICE_SUPPLIER_REQUIRED");
        this.settings = Objects.requireNonNull(settings, "RUNTIME_SKILL_SETTINGS_REQUIRED");
        this.budget = Objects.requireNonNull(budget, "SKILL_RUNTIME_BUDGET_REQUIRED");
    }
    public String render(OpsRuntimeResourceContext context) {
        SkillCatalogQueryService catalogQuery = catalogQuerySupplier.get();
        if (context == null || context.getRequest() == null) return "";
        List<Map<String,Object>> refs = refs(context, "usedSkillVersionRefs");
        if (catalogQuery == null) {
            if (!refs.isEmpty() || !refs(context, "skillCatalogRefs").isEmpty()) throw new IllegalStateException("SKILL_CATALOG_QUERY_REQUIRED");
            return "";
        }
        String summary = summary(catalogRefs(context));
        if (!settings.contextEnabled()) return summary;
        if (refs.size() > SkillRuntimeBodyPolicy.MAX_SKILLS) throw new IllegalStateException("SKILL_RUNTIME_SKILL_COUNT_EXCEEDED");
        StringBuilder result = new StringBuilder(summary);
        List<SkillRuntimeBudgetPort.Load> loads = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        int remaining = Math.min(SkillRuntimeBodyPolicy.TOTAL_BUDGET, Math.max(0, settings.contextMaxChars()));
        for (Map<String,Object> ref : refs) {
            String identity = identity(context.getProjectId(), ref);
            if (!identities.add(identity)) throw new IllegalArgumentException("SKILL_RUNTIME_DUPLICATE_VERSION_REF");
            String content;
            if ("CANARY".equals(text(ref.get("statusAtUse")))) {
                OpsSkillReleaseService release = releaseServiceSupplier.get();
                if (release == null) throw new IllegalStateException("SKILL_RELEASE_SERVICE_REQUIRED");
                content = release.renderFrozenCanaryContext(context.getProjectId(),
                        context.getDefinition() == null ? "" : context.getDefinition().getAgentId(), List.of(ref));
            } else {
                Map<String,Object> skill = catalogQuery.getRuntimeSkillVersion(context.getProjectId(),
                        required(ref, "skillId"), number(ref.get("version")), required(ref, "skillHash"),
                        required(ref, "packageHash"), required(ref, "scope"));
                content = Objects.toString(skill.get("content"), "");
            }
            // Legacy maxSingleSkillChars is an additional conservative cap, never a substring length.
            content = bodyPolicy.project(content, context.getRequest().getQuery(),
                    Math.min(remaining, Math.max(0, settings.maxSingleSkillChars())));
            remaining -= SkillRuntimeBodyPolicy.units(content);
            loads.add(SkillRuntimeBudgetPort.Load.body(identity, content));
            result.append("\n#### ").append(required(ref, "skillId")).append("\n").append(content).append('\n');
        }
        budget.reserve(context.getProjectId(), context.getRequest().getRunId(), loads);
        if (!refs.isEmpty()) result.append("\n使用 Skill 前核对其适用与禁用条件；入口引用的方法资源必须通过 UseProjectSkill 按路径读取。只读取入口或名称不能算使用了完整方法。资源不可读或预算不足时明确说明，不能猜测缺失的步骤或阈值。\n");
        return result.toString();
    }
    public List<Map<String,Object>> catalogRefs(OpsRuntimeResourceContext context) {
        List<Map<String,Object>> refs = refs(context, "skillCatalogRefs");
        if (refs.isEmpty()) return refs;
        SkillCatalogQueryService catalog = catalogQuerySupplier.get();
        if (catalog == null) throw new IllegalStateException("SKILL_CATALOG_QUERY_REQUIRED");
        return catalog.retainRuntimeCatalogRefs(context.getProjectId(), refs);
    }
    public static String identity(String project, Map<String,Object> ref) {
        if ("CANARY".equals(text(ref.get("statusAtUse")))) return project + ":CANARY:" + required(ref, "skillId")
                + ":" + required(ref, "version") + ":" + required(ref, "skillHash");
        return required(ref, "scope") + ":" + project + ":" + required(ref, "skillId") + ":"
                + required(ref, "version") + ":" + required(ref, "packageHash");
    }
    private String summary(List<Map<String,Object>> catalog) {
        StringBuilder summary = new StringBuilder();
        int limit = Math.max(0, settings.catalogSummaryMaxChars());
        for (Map<String,Object> ref : catalog) {
            // Metadata is indivisible here: never retain a positive condition while truncating its exclusion.
            String exclusion = ref.get("whenNotToUse") instanceof List<?> values ? String.join("; ", values.stream().map(String::valueOf).toList()) : "";
            String line = "- [" + text(ref.get("category")) + "] " + text(ref.get("name")) + " (`" + required(ref, "skillId") + "`): "
                    + text(ref.get("description")) + (exclusion.isBlank() ? "" : "；不适用：" + exclusion) + "\n";
            if (SkillRuntimeBodyPolicy.units(line) > SkillRuntimeBodyPolicy.CATALOG_ENTRY_BUDGET) {
                // Keep discoverability without presenting a truncated positive/negative condition as complete.
                line = "- `" + required(ref,"skillId") + "`：摘要超过预算；使用前按需读取完整条件与方法。\n";
                if (SkillRuntimeBodyPolicy.units(line) > SkillRuntimeBodyPolicy.CATALOG_ENTRY_BUDGET) continue;
            }
            if (SkillRuntimeBodyPolicy.units(summary.toString()) + SkillRuntimeBodyPolicy.units(line) > limit) break;
            summary.append(line);
        }
        return summary.isEmpty() ? "" : "### 本次 Work Session 的 Skill 轻量目录\n" + summary
                + "目录只用于选择规程；正文通过绑定版本读取，不授予工具或生产写权限。\n";
    }
    private static List<Map<String,Object>> refs(OpsRuntimeResourceContext context, String key) {
        if (context == null || context.getRequest() == null || context.getRequest().getMetadata() == null) return List.of();
        if (!(context.getRequest().getMetadata().get(key) instanceof Iterable<?> source)) return List.of();
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object value : source) {
            if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("SKILL_RUNTIME_REF_INVALID");
            Map<String,Object> ref = new LinkedHashMap<>(); map.forEach((k,v) -> ref.put(String.valueOf(k), v));
            result.add(Collections.unmodifiableMap(ref));
        }
        return List.copyOf(result);
    }
    private static String required(Map<String,Object> ref, String key) {
        String value = text(ref.get(key));
        if (value.isBlank()) throw new IllegalArgumentException("SKILL_RUNTIME_REF_INCOMPLETE");
        return value;
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static int number(Object value) {
        int version = Integer.parseInt(text(value));
        if (version <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        return version;
    }
}
