package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceInput;
import cn.lgs.orbisops.domain.skill.model.SkillExperiencePolicyResult;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;
import cn.lgs.orbisops.domain.skill.model.VerifiedTaskOutcome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Removes run-specific details before runtime facts can become reusable Skill material. */
public class SkillExperienceObservationPolicy {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f-]{27,36}\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{6,}\\b");
    private static final Pattern TRACE_TOKEN = Pattern.compile(
            "(?i)\\b(trace|request|order|container|run)[-_ ]?id\\s*[:=]\\s*[^\\s,;]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(pass(word|wd)?|secret|token|api[_-]?key|access[_-]?key|authorization)"
                    + "\\s*[:=]\\s*([^\\s,;]+)");

    public SkillExperiencePolicyResult sanitize(SkillExperienceInput input) {
        return sanitize(input, VerifiedTaskOutcome.unknown());
    }

    public SkillExperiencePolicyResult sanitize(SkillExperienceInput input, VerifiedTaskOutcome authority) {
        if (input == null) {
            throw new IllegalArgumentException("SKILL_EXPERIENCE_INPUT_REQUIRED");
        }
        boolean verified = authority != null && authority.matches(input.projectId(), input.runId());
        String observationType = value(input.observationType()).toUpperCase(Locale.ROOT);
        List<SkillExperienceEvidenceReference> evidence = input.evidenceReferences().stream()
                .filter(reference -> reference != null
                        && !value(reference.resultId()).isBlank()
                        && !value(reference.outputHash()).isBlank())
                .limit(32)
                .toList();
        SkillExperienceTaskTemplate taskTemplate = new SkillExperienceTaskTemplate(
                fallback(input.primaryIntent(), observationType).toUpperCase(Locale.ROOT),
                normalizeGoal(input.normalizedUserGoal()),
                observationType,
                evidenceTypes(evidence),
                verified ? "SUCCEEDED" : "UNVERIFIED");
        List<String> trajectory = abstractTrajectory(
                input.toolEvidence(),
                verified);
        String finalSummary = abbreviate(mask(input.finalOutput()), 2000);
        double qualityScore = qualityScore(
                verified,
                evidence,
                finalSummary);
        return new SkillExperiencePolicyResult(
                taskTemplate,
                trajectory,
                evidence,
                verified ? "SUCCEEDED" : "UNVERIFIED",
                finalSummary,
                qualityScore);
    }

    private List<String> abstractTrajectory(
            List<String> toolEvidence,
            boolean completed) {
        Set<String> actions = new LinkedHashSet<>();
        for (String raw : toolEvidence == null ? List.<String>of() : toolEvidence) {
            String item = value(raw).toLowerCase(Locale.ROOT);
            if (item.contains("prometheus") || item.contains("metric")) {
                actions.add("QUERY_METRICS");
            } else if (item.contains("elastic")
                    || item.contains(" opensearch")
                    || item.contains(" log")) {
                actions.add("QUERY_LOGS");
            } else if (item.contains("trace")) {
                actions.add("QUERY_TRACES");
            } else if (item.contains("sql")) {
                actions.add("QUERY_DATABASE");
            } else if (item.contains("rag") || item.contains("knowledge")) {
                actions.add("QUERY_KNOWLEDGE");
            } else if (item.contains("code")
                    || item.contains("grep")
                    || item.contains("read")) {
                actions.add("INSPECT_CODE");
            } else if (item.contains("test")
                    || item.contains("build")
                    || item.contains("lint")) {
                actions.add("RUN_VALIDATION");
            } else if (item.contains("dry") || item.contains("sandbox")) {
                actions.add("RUN_ISOLATED_VALIDATION");
            } else {
                actions.add("USE_CONTROLLED_TOOL");
            }
        }
        if (actions.isEmpty()) actions.add("NO_TOOL_EVIDENCE");
        if (completed) actions.add("SYNTHESIZE_VERIFIED_OUTCOME");
        return new ArrayList<>(actions);
    }

    private List<String> evidenceTypes(
            List<SkillExperienceEvidenceReference> references) {
        Set<String> types = new LinkedHashSet<>();
        for (SkillExperienceEvidenceReference reference : references) {
            String sourceType = value(reference.sourceType());
            types.add(sourceType.isBlank()
                    ? "TOOL_RESULT"
                    : sourceType.toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(types);
    }

    private String normalizeGoal(String source) {
        String normalized = mask(source).toLowerCase(Locale.ROOT);
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("{id}");
        normalized = TRACE_TOKEN.matcher(normalized).replaceAll("$1Id={id}");
        normalized = LONG_NUMBER.matcher(normalized).replaceAll("{number}");
        return abbreviate(normalized.replaceAll("\\s+", " ").trim(), 600);
    }

    private String mask(String source) {
        return SENSITIVE_ASSIGNMENT.matcher(value(source)).replaceAll("$1=***");
    }

    private double qualityScore(
            boolean completed,
            List<SkillExperienceEvidenceReference> evidence,
            String finalSummary) {
        double score = completed ? 0.45D : 0.10D;
        if (!evidence.isEmpty()) score += 0.40D;
        if (finalSummary.length() >= 80) score += 0.15D;
        return Math.min(1D, score);
    }

    private String fallback(String value, String fallback) {
        return value(value).isBlank() ? value(fallback) : value(value);
    }

    private String abbreviate(String value, int maximum) {
        return value.length() <= maximum
                ? value
                : value.substring(0, maximum) + "...";
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
