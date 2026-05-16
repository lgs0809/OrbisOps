package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import java.util.*;

/** Explicit bindings from the server-resolved definition, never the inherited project catalog. */
public record OpsExplicitSkillBindings(List<String> ids) {
    public OpsExplicitSkillBindings {
        ids = normalize(ids);
    }
    public static OpsExplicitSkillBindings capture(OpsAgentDefinition definition) {
        List<String> ids = new ArrayList<>();
        if (definition != null) {
            add(ids, definition.getSkills());
            if (definition.getNodes() != null) definition.getNodes().stream().filter(Objects::nonNull)
                    .forEach(node -> add(ids, node.getSkills()));
            if (definition.getAgentscopeAgents() != null) definition.getAgentscopeAgents().stream()
                    .filter(Objects::nonNull).forEach(agent -> add(ids, agent.getSkills()));
        }
        return new OpsExplicitSkillBindings(ids);
    }
    public List<String> merge(List<String> requested) {
        List<String> all = new ArrayList<>(ids);
        add(all, requested);
        List<String> result = normalize(all);
        if (result.size() > 3) throw new IllegalArgumentException(
                "TOO_MANY_EXPLICIT_SKILLS: Workflow 与本次请求合计最多显式绑定 3 个 Skill，请拆分任务或调整绑定");
        return result;
    }
    private static List<String> normalize(List<String> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull)
                .map(String::trim).map(SkillCatalogFingerprint::normalizeId)
                .filter(id -> !id.isBlank()).distinct().toList();
    }
    private static void add(List<String> result, List<String> ids) {
        if (ids != null) result.addAll(ids);
    }
}
