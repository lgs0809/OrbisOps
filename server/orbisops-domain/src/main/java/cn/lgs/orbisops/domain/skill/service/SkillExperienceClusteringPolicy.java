package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds a reusable task-family identity without retaining run-specific wording. */
public final class SkillExperienceClusteringPolicy {

    private static final Map<String, List<String>> PROBLEM_FAMILIES =
            problemFamilies();

    public Map<String, Object> clusterIdentity(
            SkillExperienceTaskTemplate template,
            List<String> abstractTrajectory) {
        if (template == null) {
            throw new IllegalArgumentException(
                    "SKILL_EXPERIENCE_TASK_TEMPLATE_REQUIRED");
        }
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("intent", value(template.intent()).toUpperCase(Locale.ROOT));
        identity.put("triggerType",
                value(template.triggerType()).toUpperCase(Locale.ROOT));
        identity.put("problemFamily", problemFamily(
                template.problemPattern()));
        identity.put("evidenceTypes", template.evidenceTypes().stream()
                .map(item -> value(item).toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList());
        identity.put("trajectory", (abstractTrajectory == null
                ? List.<String>of()
                : abstractTrajectory).stream()
                .map(item -> value(item).toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList());
        return Map.copyOf(identity);
    }

    public String problemFamily(String problemPattern) {
        String normalized = value(problemPattern).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry :
                PROBLEM_FAMILIES.entrySet()) {
            if (entry.getValue().stream().anyMatch(normalized::contains)) {
                return entry.getKey();
            }
        }
        return "GENERAL_TASK";
    }

    private static Map<String, List<String>> problemFamilies() {
        Map<String, List<String>> families = new LinkedHashMap<>();
        families.put("AVAILABILITY", List.of(
                "不可用", "无法访问", "宕机", "超时", "timeout",
                "unavailable", "down", "health"));
        families.put("ERROR_DIAGNOSIS", List.of(
                "报错", "错误", "失败", "异常", "error", "exception",
                "failed", "failure"));
        families.put("PERFORMANCE", List.of(
                "慢", "延迟", "性能", "吞吐", "latency", "slow",
                "performance", "throughput"));
        families.put("DATA", List.of(
                "数据库", "sql", "表", "数据", "mysql", "redis",
                "database", "query"));
        families.put("CONFIGURATION", List.of(
                "配置", "参数", "开关", "config", "configuration",
                "property"));
        families.put("DEPLOYMENT", List.of(
                "发布", "部署", "回滚", "镜像", "deploy", "release",
                "rollback", "image"));
        families.put("SECURITY", List.of(
                "权限", "凭据", "密钥", "漏洞", "security", "permission",
                "credential", "secret"));
        families.put("CODE_CHANGE", List.of(
                "代码", "修复", "编译", "测试", "code", "patch",
                "compile", "test"));
        families.put("DOCUMENT", List.of(
                "文档", "报告", "汇报", "document", "report",
                "presentation"));
        // The first matching family wins; Map.copyOf deliberately has unspecified iteration order.
        return java.util.Collections.unmodifiableMap(families);
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
