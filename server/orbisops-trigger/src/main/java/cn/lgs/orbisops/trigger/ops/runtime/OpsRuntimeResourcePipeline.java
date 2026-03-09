package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/** Applies the runtime resource rules in the single supported production order. */
@Component
public final class OpsRuntimeResourcePipeline {

    private final List<OpsRuntimeResourceRule> rules;

    public OpsRuntimeResourcePipeline(
            OpsRuntimeModelResolver modelResolver,
            OpsRuntimeMcpResolver mcpResolver,
            OpsRuntimeSkillResolver skillResolver,
            OpsRuntimeBuiltInToolContributor builtInToolContributor,
            OpsRuntimeKnowledgeResolver knowledgeResolver,
            OpsRuntimeSubAgentToolBoundaryPolicy subAgentToolBoundaryPolicy,
            OpsRuntimeToolTraceDecorator toolTraceDecorator,
            OpsRuntimeResourceSummaryAuditor summaryAuditor) {
        if (modelResolver == null
                || mcpResolver == null
                || skillResolver == null
                || builtInToolContributor == null
                || knowledgeResolver == null
                || subAgentToolBoundaryPolicy == null
                || toolTraceDecorator == null
                || summaryAuditor == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_PIPELINE_DEPENDENCIES_REQUIRED");
        }
        this.rules = List.of(
                rule("MODEL", context -> context.setChatModel(modelResolver.resolve(context))),
                rule("MCP", mcpResolver::resolve),
                rule("SKILL", skillResolver::resolve),
                rule("KNOWLEDGE_BASE", knowledgeResolver::resolve),
                rule("BUILT_IN_TOOLS", builtInToolContributor::contribute),
                rule("SUB_AGENT_TOOL_BOUNDARY", subAgentToolBoundaryPolicy::enforce),
                rule("TOOL_TRACE", toolTraceDecorator::decorate),
                rule("SUMMARY", summaryAuditor::summarize));
    }

    public OpsRuntimeResourceBundle assemble(OpsRuntimeResourceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        }
        for (OpsRuntimeResourceRule rule : rules) {
            rule.apply(context);
        }
        return context.toBundle();
    }

    public List<String> ruleNames() {
        return rules.stream().map(OpsRuntimeResourceRule::name).toList();
    }

    private OpsRuntimeResourceRule rule(
            String name,
            Consumer<OpsRuntimeResourceContext> action) {
        return new OpsRuntimeResourceRule() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void apply(OpsRuntimeResourceContext context) {
                action.accept(context);
            }
        };
    }
}
