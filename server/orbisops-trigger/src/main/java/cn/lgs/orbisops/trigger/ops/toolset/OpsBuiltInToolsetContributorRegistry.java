package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ordered immutable registry for the canonical built-in Toolset catalog. */
public final class OpsBuiltInToolsetContributorRegistry {

    private final List<OpsBuiltInToolsetContributor> contributors;
    private final List<OpsToolsetDefinition> definitions;
    private final OpsToolsetDefinitionCopier copier = new OpsToolsetDefinitionCopier();

    public OpsBuiltInToolsetContributorRegistry(
            List<OpsBuiltInToolsetContributor> contributors) {
        if (contributors == null || contributors.isEmpty()) {
            throw new IllegalArgumentException("BUILT_IN_TOOLSET_CONTRIBUTORS_REQUIRED");
        }
        Map<String, OpsBuiltInToolsetContributor> indexed = new LinkedHashMap<>();
        for (OpsBuiltInToolsetContributor contributor : contributors) {
            if (contributor == null) {
                throw new IllegalArgumentException("BUILT_IN_TOOLSET_CONTRIBUTOR_REQUIRED");
            }
            String id = normalize(contributor.contributorId());
            if (id.isBlank()) {
                throw new IllegalArgumentException("BUILT_IN_TOOLSET_CONTRIBUTOR_ID_REQUIRED");
            }
            OpsBuiltInToolsetContributor duplicate = indexed.putIfAbsent(id, contributor);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "BUILT_IN_TOOLSET_CONTRIBUTOR_DUPLICATE:" + id);
            }
        }
        List<OpsBuiltInToolsetContributor> ordered = new ArrayList<>(indexed.values());
        ordered.sort(Comparator
                .comparingInt(OpsBuiltInToolsetContributor::order)
                .thenComparing(item -> normalize(item.contributorId())));
        this.contributors = List.copyOf(ordered);
        this.definitions = List.copyOf(assemble(ordered));
    }

    public List<String> orderedContributorIds() {
        return contributors.stream()
                .map(item -> normalize(item.contributorId()))
                .toList();
    }

    public List<OpsToolsetDefinition> definitions() {
        return definitions.stream().map(copier::copy).toList();
    }

    private List<OpsToolsetDefinition> assemble(
            List<OpsBuiltInToolsetContributor> ordered) {
        Map<String, OpsToolsetDefinition> toolsets = new LinkedHashMap<>();
        for (OpsBuiltInToolsetContributor contributor : ordered) {
            List<OpsToolsetDefinition> contribution = contributor.definitions();
            if (contribution == null) {
                throw new IllegalStateException(
                        "BUILT_IN_TOOLSET_CONTRIBUTION_REQUIRED:"
                                + normalize(contributor.contributorId()));
            }
            for (OpsToolsetDefinition source : contribution) {
                OpsToolsetDefinition definition = validateAndCopy(source, contributor);
                String toolsetId = normalize(definition.getToolsetId());
                if (toolsets.putIfAbsent(toolsetId, definition) != null) {
                    throw new IllegalStateException(
                            "BUILT_IN_TOOLSET_ID_DUPLICATE:" + toolsetId);
                }
            }
        }
        validateDependencyGraph(toolsets);
        return new ArrayList<>(toolsets.values());
    }

    private OpsToolsetDefinition validateAndCopy(
            OpsToolsetDefinition source,
            OpsBuiltInToolsetContributor contributor) {
        if (source == null) {
            throw new IllegalStateException(
                    "BUILT_IN_TOOLSET_DEFINITION_REQUIRED:"
                            + normalize(contributor.contributorId()));
        }
        OpsToolsetDefinition definition = copier.copy(source);
        String toolsetId = normalize(definition.getToolsetId());
        if (toolsetId.isBlank()) {
            throw new IllegalStateException("BUILT_IN_TOOLSET_ID_REQUIRED");
        }
        if (definition.getTools() == null || definition.getTools().isEmpty()) {
            throw new IllegalStateException(
                    "BUILT_IN_TOOLSET_TOOLS_REQUIRED:" + toolsetId);
        }
        Set<String> toolNames = new LinkedHashSet<>();
        for (OpsToolDefinition tool : definition.getTools()) {
            if (tool == null || normalize(tool.getToolName()).isBlank()) {
                throw new IllegalStateException(
                        "BUILT_IN_TOOL_NAME_REQUIRED:" + toolsetId);
            }
            String toolName = normalize(tool.getToolName());
            if (!toolNames.add(toolName)) {
                throw new IllegalStateException(
                        "BUILT_IN_TOOL_NAME_DUPLICATE:"
                                + toolsetId + ":" + toolName);
            }
            if (tool.getProviderDescriptor() == null || tool.getSemantics() == null) {
                throw new IllegalStateException(
                        "BUILT_IN_TOOL_TYPED_FACTS_REQUIRED:"
                                + toolsetId + ":" + toolName);
            }
        }
        definition.setTools(definition.getTools().stream()
                .map(copier::copyTool)
                .toList());
        definition.setTags(definition.getTags() == null
                ? List.of()
                : List.copyOf(definition.getTags()));
        return definition;
    }

    private void validateDependencyGraph(
            Map<String, OpsToolsetDefinition> toolsets) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, OpsToolsetDefinition> entry : toolsets.entrySet()) {
            Set<String> parsed = parseDependencies(entry.getValue().getPrerequisites());
            for (String dependency : parsed) {
                if (!toolsets.containsKey(dependency)) {
                    throw new IllegalStateException(
                            "BUILT_IN_TOOLSET_DEPENDENCY_UNKNOWN:"
                                    + entry.getKey() + ":" + dependency);
                }
            }
            dependencies.put(entry.getKey(), parsed);
        }
        Map<String, VisitState> states = new HashMap<>();
        for (String toolsetId : dependencies.keySet()) {
            visit(toolsetId, dependencies, states);
        }
    }

    private void visit(
            String current,
            Map<String, Set<String>> dependencies,
            Map<String, VisitState> states) {
        VisitState state = states.get(current);
        if (state == VisitState.VISITING) {
            throw new IllegalStateException(
                    "BUILT_IN_TOOLSET_DEPENDENCY_CYCLE:" + current);
        }
        if (state == VisitState.VISITED) return;
        states.put(current, VisitState.VISITING);
        for (String dependency : dependencies.getOrDefault(current, Set.of())) {
            visit(dependency, dependencies, states);
        }
        states.put(current, VisitState.VISITED);
    }

    private Set<String> parseDependencies(String prerequisites) {
        String value = prerequisites == null ? "" : prerequisites.trim();
        if (value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String token : value.split("[,\\s]+")) {
            String normalized = normalize(token);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
