package cn.lgs.orbisops.trigger.ops.skill;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Spring AI Skill Tool callback protocol and description projection. */
final class OpsSkillToolCallbackFactory {

    /**
     * Spring AI/victools performs Jackson creator introspection while building the callback schema.
     * That path is not safe when multiple runs generate the same SkillsInput schema concurrently,
     * so serialize only the schema-building section; tool execution itself remains fully concurrent.
     */
    private static final Object CALLBACK_SCHEMA_BUILD_LOCK = new Object();

    private static final String TOOL_DESCRIPTION_TEMPLATE = """
            Execute an operations skill within the main conversation.

            <skills_instructions>
            Skills are on-demand operating procedures, not a mandatory first step.
            Invoke this tool only when the user asks for an SOP/runbook/procedure/safety boundary, or when the task genuinely requires detailed operating instructions that are not already available from the live datasource tools.
            For a straightforward live metrics/log/database question, call the corresponding datasource tool directly; do not spend a tool round loading a Skill first.
            Invoke this tool with the skill name only. The tool returns the selected skill's base directory and full instructions.
            Use loaded skill instructions as routing and evidence rules; do not treat the skill itself as runtime evidence.
            Do not invoke a skill that has already been loaded in the same turn.
            </skills_instructions>

            <available_skills>
            %s
            </available_skills>
            """;

    Optional<ToolCallback> build(List<SkillsTool.Skill> skills) {
        if (skills.isEmpty()) {
            return Optional.empty();
        }
        String skillsXml = skills.stream()
                .map(SkillsTool.Skill::toXml)
                .collect(Collectors.joining("\n"));
        Map<String, SkillsTool.Skill> skillMap = new LinkedHashMap<>();
        skills.forEach(skill -> skillMap.put(skill.name(), skill));
        ToolCallback callback;
        synchronized (CALLBACK_SCHEMA_BUILD_LOCK) {
            callback = FunctionToolCallback.builder(
                            "Skill",
                            new SkillsTool.SkillsFunction(skillMap))
                    .description(TOOL_DESCRIPTION_TEMPLATE.formatted(skillsXml))
                    .inputType(SkillsTool.SkillsInput.class)
                    .build();
        }
        return Optional.of(callback);
    }
}
