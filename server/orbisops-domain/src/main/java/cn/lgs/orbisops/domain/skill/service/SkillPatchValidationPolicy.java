package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchValidationDecision;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Domain governance policy for structured Skill patch candidates. */
public class SkillPatchValidationPolicy {

    private static final List<String> DANGEROUS_CONTENT = List.of(
            "绕过审批",
            "绕过沙箱",
            "绕过执行中心",
            "直接修改生产",
            "直接执行任意 sql",
            "直接重启生产",
            "删除数据",
            "关闭审计",
            "扩大 mcp 权限",
            "landingruntime 自由规划");

    private static final List<String> DANGEROUS_SCRIPT = List.of(
            "kubectl apply",
            "kubectl delete",
            "helm upgrade",
            "terraform apply",
            "terraform destroy",
            "docker restart",
            "docker rm",
            "redis-cli",
            "mysql -",
            "psql ",
            "rm -rf",
            "curl | sh",
            "wget | sh");

    public SkillPatchValidationDecision evaluate(
            SkillPatchCandidate candidate,
            List<String> regressionFailures) {
        if (candidate == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_REQUIRED");
        return evaluate(
                candidate.changes(),
                candidate.artifacts(),
                candidate.evidenceRefs(),
                candidate.projectId(),
                candidate.targetSkillId(),
                regressionFailures);
    }

    /** Compatibility entry for open authoring payloads before candidate materialization. */
    public SkillPatchValidationDecision evaluate(
            Map<String, Object> candidate,
            List<String> regressionFailures) {
        Map<String, Object> source = candidate == null ? Map.of() : candidate;
        return evaluate(
                list(source.get("changes")),
                list(source.get("artifacts")),
                list(source.get("evidenceRefs")),
                text(first(source.get("projectId"), source.get("project_id"))),
                text(first(source.get("targetSkillId"), source.get("target_skill_id"))),
                regressionFailures);
    }

    private SkillPatchValidationDecision evaluate(
            List<?> changes,
            List<?> artifacts,
            List<?> evidence,
            String projectId,
            String targetSkillId,
            List<String> regressionFailures) {
        List<String> failures = new ArrayList<>();

        if (changes.isEmpty()) failures.add("SCHEMA_EMPTY_CHANGES");
        for (Object item : changes) {
            if (!(item instanceof Map<?, ?> map)
                    || !map.containsKey("section")
                    || !map.containsKey("operation")
                    || !map.containsKey("key")) {
                failures.add("SCHEMA_INVALID_CHANGE");
            }
        }
        try {
            SkillRoutingProfile profile = routingProfile(changes);
            new SkillRoutingProfilePolicy().requireProfile(
                    profile.category(),
                    profile.subcategory(),
                    "candidate",
                    "",
                    "",
                    profile.useCases(),
                    profile.exclusions(),
                    profile.keywords());
            failures.addAll(new SkillRoutingBoundaryPolicy().validate(profile));
        } catch (IllegalArgumentException error) {
            failures.add("SKILL_ROUTING_PROFILE_INVALID:" + error.getMessage());
        }

        String lower = (String.valueOf(changes) + String.valueOf(artifacts))
                .toLowerCase(Locale.ROOT);
        DANGEROUS_CONTENT.stream()
                .filter(lower::contains)
                .forEach(item -> failures.add("POLICY_DANGEROUS_CONTENT"));
        DANGEROUS_SCRIPT.stream()
                .filter(lower::contains)
                .forEach(item -> failures.add("POLICY_DANGEROUS_SCRIPT"));

        try {
            List<String> evalSuites = artifacts.stream()
                    .filter(item -> item instanceof Map<?, ?> map
                            && "EVAL".equalsIgnoreCase(String.valueOf(map.get("role"))))
                    .map(item -> String.valueOf(((Map<?, ?>) item).get("path")))
                    .toList();
            SkillPackageManifest.packageOf(
                    "PROJECT",
                    projectId,
                    fallback(targetSkillId, "candidate"),
                    "candidate",
                    "candidate",
                    1,
                    "# Candidate\n\n候选方法只在审核前使用。",
                    artifacts,
                    List.of(),
                    evalSuites,
                    SkillPackageManifest.Limits.defaults());
        } catch (IllegalArgumentException error) {
            failures.add("SKILL_PACKAGE_INVALID:" + error.getMessage());
        }

        if (evidence.isEmpty()) {
            failures.add("EVIDENCE_ATTRIBUTION_MISSING");
        }
        if (lower.contains("mustalways") && lower.contains("verified=false")) {
            failures.add("UNVERIFIED_MEMORY_STRONG_RULE");
        }
        if (regressionFailures != null) failures.addAll(regressionFailures);

        String status = failures.stream().anyMatch(failure -> failure.startsWith("POLICY"))
                ? "POLICY_REJECTED"
                : failures.isEmpty() ? "PASSED" : "VALIDATION_FAILED";
        return new SkillPatchValidationDecision(
                status,
                failures.isEmpty(),
                failures.stream().distinct().toList());
    }

    private List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private SkillRoutingProfile routingProfile(List<?> changes) {
        for (Object item : changes) {
            if (!(item instanceof Map<?, ?> change)
                    || !"routingProfile".equals(text(change.get("section")))) {
                continue;
            }
            if (!(change.get("value") instanceof Map<?, ?> value)) break;
            return new SkillRoutingProfilePolicy().profile(
                    text(value.get("category")),
                    text(value.get("subcategory")),
                    "candidate",
                    "",
                    "",
                    value.get("whenToUse"),
                    value.get("whenNotToUse"),
                    value.get("keywords"));
        }
        throw new IllegalArgumentException("SKILL_ROUTING_PROFILE_REQUIRED");
    }

    private Object first(Object left, Object right) {
        return left == null || text(left).isBlank() ? right : left;
    }

    private String fallback(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
