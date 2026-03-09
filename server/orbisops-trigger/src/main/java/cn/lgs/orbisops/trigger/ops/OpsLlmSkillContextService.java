package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Plain boundary for Skill summaries, eager content and model tool binding. */
final class OpsLlmSkillContextService {

    private static final Logger log = LoggerFactory.getLogger(OpsLlmSkillContextService.class);

    private final Supplier<SkillRuntimeToolProvider> providerSupplier;

    OpsLlmSkillContextService(Supplier<SkillRuntimeToolProvider> providerSupplier) {
        this.providerSupplier = providerSupplier;
    }

    String withLazyContext(
            String systemPrompt,
            Collection<String> skillNames,
            boolean enabled,
            int maxChars) {
        if (!enabled || skillNames == null || skillNames.isEmpty()) {
            return systemPrompt;
        }
        SkillRuntimeToolProvider provider = provider();
        if (provider == null) {
            return systemPrompt;
        }
        try {
            String summary = provider.renderSkillSummaryContext(skillNames, maxChars);
            if (!hasText(summary)) {
                return systemPrompt;
            }
            return systemPrompt + """

                    ### 可用 Skill 与使用边界
                    当前 Agent / 节点绑定了以下 Skill。Skill 是操作规程，不是事实证据；事实只能来自用户输入、RAG、MCP 查询结果和 observation。
                    如当前任务需要具体操作规程，必须先调用 Skill 工具读取对应 Skill 正文；不要使用未绑定 Skill，不要把 Skill 当成查询结果。

                    %s
                    """.formatted(summary);
        } catch (Exception e) {
            log.warn("渲染 lazy Skill 摘要失败：{}", e.getMessage());
            return systemPrompt;
        }
    }

    String withEagerContext(
            String systemPrompt,
            Collection<String> skillNames,
            int maxChars) {
        if (skillNames == null || skillNames.isEmpty()) {
            return systemPrompt;
        }
        SkillRuntimeToolProvider provider = provider();
        if (provider == null) {
            return systemPrompt;
        }
        try {
            String skillContext = provider.renderSkillContext(
                    skillNames,
                    Math.max(2000, maxChars));
            if (!hasText(skillContext)) {
                return systemPrompt;
            }
            return systemPrompt + """

                    ### 本次已加载 Skill 正文
                    以下内容仅在本次模型调用中生效，用于指导路由、查询口径和证据判断；不要把 Skill 当成事实证据。

                    %s
                    """.formatted(skillContext);
        } catch (Exception e) {
            log.warn("渲染 eager Skill 正文失败：{}", e.getMessage());
            return systemPrompt;
        }
    }

    Optional<ToolCallback> skillTool(Collection<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return Optional.empty();
        }
        SkillRuntimeToolProvider provider = provider();
        return provider == null
                ? Optional.empty()
                : provider.buildSkillToolCallback(skillNames);
    }

    List<String> normalizedNames(Collection<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        skillNames.stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(names::add);
        return List.copyOf(names);
    }

    private SkillRuntimeToolProvider provider() {
        return providerSupplier == null ? null : providerSupplier.get();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
