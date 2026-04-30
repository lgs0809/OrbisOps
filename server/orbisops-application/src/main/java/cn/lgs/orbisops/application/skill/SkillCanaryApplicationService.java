package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillCanarySelectionPolicy;

/** Application facade for stable Skill canary exposure. */
public class SkillCanaryApplicationService {

    private final SkillCanarySelectionPolicy selectionPolicy;
    private final SkillCanarySettings settings;

    public SkillCanaryApplicationService(
            SkillCanarySelectionPolicy selectionPolicy,
            SkillCanarySettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("SKILL_CANARY_SETTINGS_REQUIRED");
        }
        this.selectionPolicy = selectionPolicy == null
                ? new SkillCanarySelectionPolicy()
                : selectionPolicy;
        this.settings = settings;
    }

    public boolean selected(
            String projectId,
            String agentId,
            String runId) {
        return selectionPolicy.selected(
                projectId,
                agentId,
                runId,
                settings.enabled(),
                settings.percent());
    }

    public int percent() {
        return selectionPolicy.percent(
                settings.enabled(),
                settings.percent());
    }
}
