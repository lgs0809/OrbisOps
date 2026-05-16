package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;

import java.util.LinkedHashSet;
import java.util.Set;

/** Explicit typed authority facts attached to one exposed runtime Tool callback. */
public record OpsRuntimeToolAuthorityDescriptor(
        String source,
        ToolSemantics semantics,
        Set<AgentExecutionStage> allowedStages,
        Set<String> aliases,
        boolean delegatedInvocationPolicy) {

    public OpsRuntimeToolAuthorityDescriptor {
        source = text(source);
        if (source.isBlank()) throw new IllegalArgumentException("RUNTIME_TOOL_AUTHORITY_SOURCE_REQUIRED");
        if (semantics == null) throw new IllegalArgumentException("RUNTIME_TOOL_SEMANTICS_REQUIRED");
        allowedStages = stages(allowedStages);
        aliases = strings(aliases);
        if (allowedStages.isEmpty()) {
            throw new IllegalArgumentException("RUNTIME_TOOL_ALLOWED_STAGES_REQUIRED");
        }
    }

    public static OpsRuntimeToolAuthorityDescriptor readOnly(
            String source, Set<AgentExecutionStage> stages, Set<String> aliases) {
        return new OpsRuntimeToolAuthorityDescriptor(
                source, ToolSemantics.readOnlyTool(), stages, aliases, false);
    }

    public static OpsRuntimeToolAuthorityDescriptor repairWorkspace(
            String source, Set<AgentExecutionStage> stages, Set<String> aliases) {
        return new OpsRuntimeToolAuthorityDescriptor(
                source, ToolSemantics.repairWorkspaceWrite(), stages, aliases, false);
    }

    public static OpsRuntimeToolAuthorityDescriptor workflow(
            String source, Set<AgentExecutionStage> stages, Set<String> aliases) {
        return new OpsRuntimeToolAuthorityDescriptor(
                source, ToolSemantics.workflowCommand(), stages, aliases, false);
    }

    public static OpsRuntimeToolAuthorityDescriptor changePackage(
            String source, Set<AgentExecutionStage> stages, Set<String> aliases) {
        return new OpsRuntimeToolAuthorityDescriptor(
                source, ToolSemantics.changePackageCommand(), stages, aliases, false);
    }

    public static OpsRuntimeToolAuthorityDescriptor targetWrite(
            String source, Set<AgentExecutionStage> stages, Set<String> aliases) {
        return new OpsRuntimeToolAuthorityDescriptor(
                source, ToolSemantics.targetResourceWrite(), stages, aliases, false);
    }

    /** Dispatcher/catalog callback; concrete remote call is checked by MCP runtime policy. */
    public static OpsRuntimeToolAuthorityDescriptor delegated(
            String source, Set<AgentExecutionStage> stages, Set<String> aliases) {
        return new OpsRuntimeToolAuthorityDescriptor(
                source, ToolSemantics.validation(), stages, aliases, true);
    }

    public boolean allowsStage(AgentExecutionStage stage) {
        return stage != null && allowedStages.contains(stage);
    }

    private static Set<AgentExecutionStage> stages(Set<AgentExecutionStage> source) {
        return source == null || source.isEmpty() ? Set.of() : Set.copyOf(source);
    }

    private static Set<String> strings(Set<String> source) {
        if (source == null || source.isEmpty()) return Set.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        source.stream().map(OpsRuntimeToolAuthorityDescriptor::text)
                .filter(value -> !value.isBlank())
                .forEach(values::add);
        return Set.copyOf(values);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
