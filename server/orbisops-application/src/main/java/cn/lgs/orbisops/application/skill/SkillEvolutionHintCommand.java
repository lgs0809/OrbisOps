package cn.lgs.orbisops.application.skill;

/** Typed application command for creating one Skill Evolution authoring hint. */
public record SkillEvolutionHintCommand(
        String signalId,
        String projectId,
        String runId,
        String hintType,
        String contentJson) {

    public SkillEvolutionHintCommand {
        signalId = value(signalId);
        projectId = value(projectId);
        runId = value(runId);
        hintType = value(hintType);
        contentJson = contentJson == null || contentJson.trim().isBlank() ? "{}" : contentJson.trim();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
