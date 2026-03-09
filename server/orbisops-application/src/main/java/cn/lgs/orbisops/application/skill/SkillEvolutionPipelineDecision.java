package cn.lgs.orbisops.application.skill;

public record SkillEvolutionPipelineDecision(
        String status,
        String reasonCode,
        String targetSkillId,
        String matchedSkillId,
        String payloadJson,
        String validationStatus) {

    public SkillEvolutionPipelineDecision {
        status = fallback(status, "CANDIDATE");
        reasonCode = text(reasonCode);
        targetSkillId = text(targetSkillId);
        matchedSkillId = text(matchedSkillId);
        payloadJson = json(payloadJson);
        validationStatus = text(validationStatus);
    }

    public boolean skipped() {
        return "SKIPPED".equalsIgnoreCase(status);
    }

    private static String json(String value) {
        String normalized = text(value);
        return normalized.isBlank() ? "{}" : normalized;
    }

    private static String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
