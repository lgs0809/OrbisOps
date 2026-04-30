package cn.lgs.orbisops.trigger.ops.skill;

import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Minimal Skill capability required by runtime assembly and LLM execution. */
public interface SkillRuntimeToolProvider {

    Optional<ToolCallback> buildSkillToolCallback(Collection<String> requiredSkillNames);

    String renderSkillContext(Collection<String> requiredSkillNames, int maxChars);

    String renderSkillSummaryContext(Collection<String> requiredSkillNames, int maxChars);

    List<OpsSkillToolProvider.SkillSummary> listSkillSummaries();
}
