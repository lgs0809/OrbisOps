package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Produces runtime readiness notes for skills referenced by an agent definition. */
final class OpsAnalysisSkillReadinessInspector {

    private final Supplier<SkillRuntimeToolProvider> skillToolProviderSupplier;

    OpsAnalysisSkillReadinessInspector(
            Supplier<SkillRuntimeToolProvider> skillToolProviderSupplier) {
        if (skillToolProviderSupplier == null) {
            throw new IllegalArgumentException("SKILL_TOOL_PROVIDER_SUPPLIER_REQUIRED");
        }
        this.skillToolProviderSupplier = skillToolProviderSupplier;
    }

    List<String> inspect(OpsAgentDefinition definition) {
        LinkedHashSet<String> required = new LinkedHashSet<>();
        Optional.ofNullable(definition.getSkills())
                .orElse(List.of())
                .forEach(required::add);
        Optional.ofNullable(definition.getNodes())
                .orElse(List.of())
                .forEach(node -> Optional.ofNullable(node.getSkills())
                        .orElse(List.of())
                        .forEach(required::add));
        if (required.isEmpty()) {
            return List.of();
        }
        SkillRuntimeToolProvider provider = skillToolProviderSupplier.get();
        if (provider == null) {
            return List.of(
                    "Agent 定义声明了 skill，但 SkillToolProvider 未初始化："
                            + String.join(",", required));
        }
        Set<String> loaded = provider.listSkillSummaries().stream()
                .map(summary -> summary.name())
                .collect(Collectors.toSet());
        List<String> missing = required.stream()
                .filter(skill -> !loaded.contains(skill))
                .toList();
        return missing.isEmpty()
                ? List.of("Agent Skill 已加载：" + String.join(",", required))
                : List.of(
                "Agent Skill 缺失：" + String.join(",", missing)
                        + "；已加载：" + String.join(",", loaded));
    }
}
