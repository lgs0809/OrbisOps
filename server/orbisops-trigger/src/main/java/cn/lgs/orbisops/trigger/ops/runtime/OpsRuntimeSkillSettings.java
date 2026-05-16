package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable settings for runtime Skill tool and context assembly. */
@Component
public final class OpsRuntimeSkillSettings {

    private final boolean contextEnabled;
    private final String contextMode;
    private final int contextMaxChars;
    private final int maxSingleSkillChars;
    private final int catalogSummaryMaxChars;

    public OpsRuntimeSkillSettings(
            @Value("${orbisops.multi-agent.skill-context-enabled:true}") boolean contextEnabled,
            @Value("${orbisops.multi-agent.skill-context-mode:lazy}") String contextMode,
            @Value("${orbisops.multi-agent.skill-context-max-chars:12000}") int contextMaxChars,
            @Value("${orbisops.skill-runtime.max-single-skill-chars:4000}") int maxSingleSkillChars,
            @Value("${orbisops.skill-runtime.catalog-summary-max-chars:2500}") int catalogSummaryMaxChars) {
        this.contextEnabled = contextEnabled;
        this.contextMode = contextMode == null ? "lazy" : contextMode;
        this.contextMaxChars = contextMaxChars;
        this.maxSingleSkillChars = maxSingleSkillChars;
        this.catalogSummaryMaxChars = catalogSummaryMaxChars;
    }

    public boolean contextEnabled() {
        return contextEnabled;
    }

    public String contextMode() {
        return contextMode;
    }

    public int contextMaxChars() {
        return contextMaxChars;
    }

    public int maxSingleSkillChars() {
        return maxSingleSkillChars;
    }

    public int catalogSummaryMaxChars() {
        return catalogSummaryMaxChars;
    }

    public static OpsRuntimeSkillSettings forTest(
            boolean contextEnabled,
            String contextMode,
            int contextMaxChars,
            int maxSingleSkillChars,
            int catalogSummaryMaxChars) {
        return new OpsRuntimeSkillSettings(
                contextEnabled,
                contextMode,
                contextMaxChars,
                maxSingleSkillChars,
                catalogSummaryMaxChars);
    }
}
