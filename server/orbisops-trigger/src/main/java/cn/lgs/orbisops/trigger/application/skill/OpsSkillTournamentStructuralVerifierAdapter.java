package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillPatchRegressionEvaluationPort;
import cn.lgs.orbisops.application.skill.SkillPatchRegressionResult;
import cn.lgs.orbisops.application.skill.SkillStructuralVerifierPort;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic structural and security verifier before hidden behavior replay. */
@Component
public final class OpsSkillTournamentStructuralVerifierAdapter
        implements SkillStructuralVerifierPort {

    private static final Set<String> SAFE_EXECUTION_MODES = Set.of(
            "READ_ONLY", "SANDBOX_DRY_RUN", "READ_ONLY_OR_SANDBOX");

    private final SkillPatchRegressionEvaluationPort regression;

    public OpsSkillTournamentStructuralVerifierAdapter(
            SkillPatchRegressionEvaluationPort regression) {
        if (regression == null) {
            throw new IllegalArgumentException("SKILL_STRUCTURAL_REGRESSION_PORT_REQUIRED");
        }
        this.regression = regression;
    }

    @Override
    public SkillStructuralVerification verify(
            SkillTournamentCandidate candidate,
            SkillVerifierVersion verifierVersion) {
        throw new IllegalStateException("SKILL_STRUCTURAL_CONTEXT_REQUIRED");
    }

    @Override
    public SkillStructuralVerification verify(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate) {
        if (context == null || candidate == null) {
            throw new IllegalArgumentException("SKILL_STRUCTURAL_INPUT_REQUIRED");
        }
        List<String> reasons = new ArrayList<>();
        SkillEvolutionAuthoredCandidate authored = candidate.authoredCandidate();
        String expectedHash = CanonicalObjectHasher.sha256(authored.payload());
        if (!expectedHash.equals(candidate.candidateHash())) {
            reasons.add("SKILL_STRUCTURAL_CANDIDATE_HASH_MISMATCH");
        }
        if (!authored.reusableChange()) {
            reasons.add("SKILL_STRUCTURAL_REUSABLE_CHANGE_REQUIRED");
        }
        if (authored.evalCases().isEmpty()) {
            reasons.add("SKILL_STRUCTURAL_EVAL_CASES_REQUIRED");
        }
        validateChanges(authored.changes(), reasons);
        validateArtifacts(authored.artifacts(), reasons);
        validateSecurityBoundary(authored.payload(), reasons);
        validateOptionalTarget(context, authored.payload(), reasons);

        SkillPatchRegressionResult regressionResult = null;
        if (reasons.isEmpty()) {
            try {
                regressionResult = regression.evaluate(regressionInput(context, authored));
                if (regressionResult == null) {
                    reasons.add("SKILL_STRUCTURAL_REGRESSION_RESULT_MISSING");
                } else if (!regressionResult.passed()) {
                    reasons.addAll(regressionResult.failures());
                }
            } catch (RuntimeException error) {
                reasons.add("SKILL_STRUCTURAL_REGRESSION_UNAVAILABLE:"
                        + error.getClass().getSimpleName());
            }
        }
        List<String> distinct = reasons.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        int evaluated = regressionResult == null ? 0 : regressionResult.evaluatedCount();
        double score = distinct.isEmpty()
                ? 1D
                : Math.max(0D, 1D - Math.min(1D, distinct.size() / (double) Math.max(4, evaluated + 4)));
        return new SkillStructuralVerification(
                candidate.candidateId(),
                distinct.isEmpty(),
                score,
                distinct,
                context.verifierVersion().structuralVersion());
    }

    private void validateChanges(
            List<Map<String, Object>> changes,
            List<String> reasons) {
        Set<String> identities = new HashSet<>();
        for (Map<String, Object> change : changes) {
            String section = text(change.get("section"));
            String key = text(change.get("key"));
            String operation = text(change.get("operation")).toUpperCase(Locale.ROOT);
            if (section.isBlank() || key.isBlank()) {
                reasons.add("SKILL_STRUCTURAL_CHANGE_IDENTITY_REQUIRED");
                continue;
            }
            if (!identities.add(section + "\u0000" + key)) {
                reasons.add("SKILL_STRUCTURAL_CHANGE_DUPLICATE:" + section + ":" + key);
            }
            if (!Set.of("ADD", "UPDATE", "UPSERT", "DELETE", "REMOVE", "REPLACE")
                    .contains(operation)) {
                reasons.add("SKILL_STRUCTURAL_CHANGE_OPERATION_INVALID:" + operation);
            }
        }
    }

    private void validateArtifacts(
            List<Map<String, Object>> artifacts,
            List<String> reasons) {
        Set<String> paths = new HashSet<>();
        for (Map<String, Object> artifact : artifacts) {
            String path = text(artifact.get("path"));
            if (path.isBlank()) {
                reasons.add("SKILL_STRUCTURAL_ARTIFACT_PATH_REQUIRED");
            } else if (!paths.add(path)) {
                reasons.add("SKILL_STRUCTURAL_ARTIFACT_DUPLICATE:" + path);
            }
        }
    }

    private void validateSecurityBoundary(
            Map<String, Object> payload,
            List<String> reasons) {
        Map<String, Object> security = map(payload.get("securityBoundary"));
        if (security.isEmpty()
                || bool(security.get("productionWriteAllowed"), true)
                || !bool(security.get("changePackageOnly"), false)) {
            reasons.add("SKILL_STRUCTURAL_SECURITY_BOUNDARY_INVALID");
        }
        Map<String, Object> tools = map(payload.get("toolBoundary"));
        String mode = text(tools.get("executionMode")).toUpperCase(Locale.ROOT);
        if (tools.isEmpty()
                || bool(tools.get("directLandingAllowed"), true)
                || !SAFE_EXECUTION_MODES.contains(mode)) {
            reasons.add("SKILL_STRUCTURAL_TOOL_BOUNDARY_INVALID");
        }
    }

    private void validateOptionalTarget(
            SkillCandidateTournamentContext context,
            Map<String, Object> payload,
            List<String> reasons) {
        String targetSkillId = text(first(
                payload.get("targetSkillId"), payload.get("target_skill_id")));
        if (!targetSkillId.isBlank() && !context.skillId().equals(targetSkillId)) {
            reasons.add("SKILL_STRUCTURAL_TARGET_SKILL_MISMATCH");
        }
        long baseVersion = number(first(
                payload.get("baseSkillVersion"), payload.get("base_skill_version")));
        if (baseVersion > 0 && baseVersion != context.baseVersion()) {
            reasons.add("SKILL_STRUCTURAL_BASE_VERSION_MISMATCH");
        }
        String baseHash = text(first(
                payload.get("baseSkillHash"), payload.get("base_skill_hash"))).toLowerCase();
        if (!baseHash.isBlank() && !context.baseSkillHash().equals(baseHash)) {
            reasons.add("SKILL_STRUCTURAL_BASE_HASH_MISMATCH");
        }
    }

    private Map<String, Object> regressionInput(
            SkillCandidateTournamentContext context,
            SkillEvolutionAuthoredCandidate authored) {
        Map<String, Object> input = new LinkedHashMap<>(authored.payload());
        input.put("project_id", context.projectId());
        input.put("target_skill_id", context.skillId());
        input.put("base_skill_version", context.baseVersion());
        input.put("base_skill_hash", context.baseSkillHash());
        input.put("patch_type", authored.patchType());
        input.put("changes_json", authored.changes());
        input.put("artifacts_json", authored.artifacts());
        input.put("eval_cases_json", authored.evalCases());
        input.putIfAbsent("evidence_refs_json", List.of());
        return Map.copyOf(input);
    }

    private Object first(Object first, Object second) {
        return first == null || text(first).isBlank() ? second : first;
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = text(value);
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
