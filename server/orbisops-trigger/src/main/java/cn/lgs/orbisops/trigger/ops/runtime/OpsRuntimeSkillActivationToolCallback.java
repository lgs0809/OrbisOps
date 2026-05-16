package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Records Skill activation only when the model actually loads a Skill body. */
final class OpsRuntimeSkillActivationToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final List<String> skillNames;
    private final Consumer<OpsRuntimeEvent> eventSink;

    OpsRuntimeSkillActivationToolCallback(
            ToolCallback delegate,
            Collection<String> skillNames,
            Consumer<OpsRuntimeEvent> eventSink) {
        if (delegate == null) throw new IllegalArgumentException("SKILL_TOOL_CALLBACK_REQUIRED");
        this.delegate = delegate;
        this.skillNames = skillNames == null ? List.of() : skillNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        this.eventSink = eventSink;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String output = delegate.call(toolInput);
        recordLoaded(toolInput);
        return output;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String output = delegate.call(toolInput, toolContext);
        recordLoaded(toolInput);
        return output;
    }

    private void recordLoaded(String toolInput) {
        String selected = selectedSkill(toolInput);
        if (!StringUtils.hasText(selected) || eventSink == null) return;
        eventSink.accept(OpsRuntimeEvent.builder()
                .eventType("SKILL_CONTEXT_LOADED")
                .nodeType("SKILL_CONTEXT")
                .agent(selected)
                .status("SUCCEEDED")
                .summary("Skill 已按需加载：" + selected)
                .payload(Map.of(
                        "skillId", selected,
                        "mode", "ON_DEMAND"))
                .build());
    }

    private String selectedSkill(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return "";
        for (String skillName : skillNames) {
            if (toolInput.contains(skillName)) return skillName;
        }
        return "";
    }
}
