package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class OpsRuntimeToolContributorRegistry {

    private static final Set<String> CRITICAL_CONTRIBUTOR_IDS = Set.of(
            "repair", "change-package", "inspection-task", "channel");

    private final List<OpsRuntimeToolContributor> contributors;
    private final Map<String, OpsRuntimeToolContributor> contributorsById;

    public OpsRuntimeToolContributorRegistry(List<OpsRuntimeToolContributor> contributors) {
        if (contributors == null) {
            throw new IllegalArgumentException("RUNTIME_TOOL_CONTRIBUTORS_REQUIRED");
        }
        Map<String, OpsRuntimeToolContributor> indexed = new LinkedHashMap<>();
        for (OpsRuntimeToolContributor contributor : contributors) {
            if (contributor == null) {
                throw new IllegalArgumentException("RUNTIME_TOOL_CONTRIBUTOR_REQUIRED");
            }
            String id = normalizeId(contributor.id());
            if (id.isBlank()) {
                throw new IllegalArgumentException("RUNTIME_TOOL_CONTRIBUTOR_ID_REQUIRED："
                        + contributor.getClass().getName());
            }
            if (contributor.requirement() == null) {
                throw new IllegalArgumentException("RUNTIME_TOOL_CONTRIBUTOR_REQUIREMENT_REQUIRED：" + id);
            }
            OpsRuntimeToolContributor duplicate = indexed.putIfAbsent(id, contributor);
            if (duplicate != null) {
                throw new IllegalStateException("RUNTIME_TOOL_CONTRIBUTOR_DUPLICATE：" + id
                        + "：" + duplicate.getClass().getName()
                        + "：" + contributor.getClass().getName());
            }
        }
        Set<String> missing = new LinkedHashSet<>(CRITICAL_CONTRIBUTOR_IDS);
        missing.removeAll(indexed.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_MISSING："
                    + String.join(",", missing));
        }
        for (String criticalId : CRITICAL_CONTRIBUTOR_IDS) {
            OpsRuntimeToolContributor contributor = indexed.get(criticalId);
            if (contributor.requirement() != OpsRuntimeToolContributorRequirement.REQUIRED) {
                throw new IllegalStateException("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_NOT_REQUIRED："
                        + criticalId);
            }
        }
        List<OpsRuntimeToolContributor> ordered = new ArrayList<>(indexed.values());
        ordered.sort(Comparator
                .comparingInt(OpsRuntimeToolContributor::order)
                .thenComparing(item -> normalizeId(item.id())));
        this.contributors = List.copyOf(ordered);
        this.contributorsById = Map.copyOf(indexed);
    }

    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        if (context.getTools() == null) context.setTools(new ArrayList<>());
        if (context.getMetadata() == null) context.setMetadata(new LinkedHashMap<>());
        validateNoDuplicateTools(context.getTools());
        for (OpsRuntimeToolContributor contributor : contributors) {
            Map<String, Object> metadataBefore = new LinkedHashMap<>(context.getMetadata());
            contributor.contribute(context);
            requireNoMetadataOverwrite(contributor, metadataBefore, context.getMetadata());
            validateNoDuplicateTools(context.getTools());
        }
    }

    public List<String> orderedContributorIds() {
        return contributors.stream().map(item -> normalizeId(item.id())).toList();
    }

    public OpsRuntimeToolContributor require(String id) {
        OpsRuntimeToolContributor contributor = contributorsById.get(normalizeId(id));
        if (contributor == null) {
            throw new IllegalArgumentException("RUNTIME_TOOL_CONTRIBUTOR_UNKNOWN：" + id);
        }
        return contributor;
    }

    private void requireNoMetadataOverwrite(OpsRuntimeToolContributor contributor,
                                            Map<String, Object> before,
                                            Map<String, Object> after) {
        if (after == null) {
            throw new IllegalStateException("RUNTIME_TOOL_METADATA_REMOVED：" + contributor.id());
        }
        for (Map.Entry<String, Object> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())
                    || !java.util.Objects.equals(entry.getValue(), after.get(entry.getKey()))) {
                throw new IllegalStateException("RUNTIME_TOOL_METADATA_OVERWRITE："
                        + normalizeId(contributor.id()) + "：" + entry.getKey());
            }
        }
    }

    private void validateNoDuplicateTools(List<ToolCallback> tools) {
        if (tools == null) throw new IllegalStateException("RUNTIME_TOOL_LIST_REMOVED");
        Set<ToolCallback> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> names = new LinkedHashSet<>();
        for (ToolCallback tool : tools) {
            if (tool == null) throw new IllegalStateException("RUNTIME_TOOL_CALLBACK_NULL");
            if (!identities.add(tool)) {
                throw new IllegalStateException("RUNTIME_TOOL_CALLBACK_DUPLICATE_INSTANCE");
            }
            String name = tool.getToolDefinition() == null
                    ? ""
                    : OpsRuntimeToolContributionSupport.stringValue(tool.getToolDefinition().name()).trim();
            if (!name.isBlank() && !names.add(name)) {
                throw new IllegalStateException("RUNTIME_TOOL_NAME_DUPLICATE：" + name);
            }
        }
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
