package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.*;
import org.springframework.ai.tool.ToolCallback;
import java.util.*;
import java.util.function.Supplier;

/** All analysis-model Skill access shares frozen versions, current authorization and the persistent run budget. */
final class OpsLlmSkillContextService {
    private final Supplier<OpsRuntimeSkillResolver> resolverSupplier;
    OpsLlmSkillContextService(Supplier<OpsRuntimeSkillResolver> resolverSupplier) {
        this.resolverSupplier = resolverSupplier;
    }
    String withLazyContext(String prompt, Collection<String> names, boolean enabled, int maxChars) {
        return enabled ? withContext(prompt, names, "LAZY") : prompt;
    }
    String withEagerContext(String prompt, Collection<String> names, int maxChars) {
        return withContext(prompt, names, "EAGER");
    }
    private String withContext(String prompt, Collection<String> names, String mode) {
        OpsRuntimeResourceContext context = context(names);
        if (context == null) return prompt;
        try {
            resolver().resolve(context);
            String content = context.getSkillContext();
            if (content == null || content.isBlank()) return prompt;
            OpsLlmTraceContext.Trace trace = OpsLlmTraceContext.current();
            // These refs have been resolved and reserved successfully; caller names are not evidence of loading.
            for (Map<String,Object> ref : trace.skillFrame().selectedRefs()) trace.record(OpsRuntimeEvent.builder()
                    .eventType("SKILL_CONTEXT_LOADED").nodeId(trace.nodeId()).nodeType("SKILL_CONTEXT")
                    .agent(String.valueOf(ref.get("skillId"))).source(trace.source()).status("SUCCEEDED")
                    .summary("已加载本次运行冻结的 Skill 正文")
                    .payload(Map.of("skillRef", ref, "mode", mode, "owner", trace.owner())).build());
            return prompt + "\n\n### 本次受治理 Skill 上下文\n"
                    + "Skill 是操作规程，不是事实证据，不授予工具或生产写权限。\n" + content;
        } catch (OpsLlmSkillContextException error) { throw error; }
        catch (RuntimeException error) { throw new OpsLlmSkillContextException(error); }
    }
    Optional<ToolCallback> skillTool(Collection<String> names) {
        OpsRuntimeResourceContext context = context(names);
        if (context == null) return Optional.empty();
        try { return resolver().catalogTool(context); }
        catch (RuntimeException error) { throw new OpsLlmSkillContextException(error); }
    }
    List<String> normalizedNames(Collection<String> names) {
        return names == null ? List.of() : names.stream().filter(Objects::nonNull)
                .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }
    private OpsRuntimeResourceContext context(Collection<String> names) {
        OpsLlmTraceContext.Trace trace = OpsLlmTraceContext.current();
        if (trace == null || trace.skillFrame() == null) {
            if (!normalizedNames(names).isEmpty()) throw new OpsLlmSkillContextException("SKILL_RUNTIME_CONTEXT_REQUIRED");
            return null;
        }
        return trace.skillFrame().context();
    }
    private OpsRuntimeSkillResolver resolver() {
        OpsRuntimeSkillResolver resolver = resolverSupplier == null ? null : resolverSupplier.get();
        if (resolver == null) throw new OpsLlmSkillContextException("SKILL_RUNTIME_RESOLVER_REQUIRED");
        return resolver;
    }
}
