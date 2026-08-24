package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.trigger.ops.runtime.*;
import java.util.*;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Explicit synthetic body and model-free resolver integration fixture. */
final class OpsLlmFrozenSkillTestFixture {
    final SkillCatalogQueryService catalog = mock(SkillCatalogQueryService.class);
    final SkillRuntimeBudgetPort budget = mock(SkillRuntimeBudgetPort.class);
    final OpsProjectSkillToolProvider tools = mock(OpsProjectSkillToolProvider.class);
    final OpsRuntimeSkillResolver resolver;
    final OpsRuntimeSkillFrame frame;
    final List<OpsRuntimeEvent> events = new ArrayList<>();
    OpsLlmFrozenSkillTestFixture(String id, String body) {
        Map<String,Object> ref = Map.of("skillId", id, "scope", "PROJECT", "projectId", "p",
                "version", 7, "skillHash", "frozen-hash", "packageHash", "package-hash");
        // Catalog can be empty: an already selected immutable ref is sufficient for eager body loading.
        frame = new OpsRuntimeSkillFrame("p", "u", "run", "workflow", "question", List.of(), List.of(ref));
        when(catalog.getRuntimeSkillVersion("p", id, 7, "frozen-hash", "package-hash", "PROJECT"))
                .thenReturn(Map.of("content", body));
        var settings = OpsRuntimeSkillSettings.forTest(true, "lazy", 6000, 6000, 2500);
        resolver = new OpsRuntimeSkillResolver(() -> null, () -> null, () -> tools,
                new OpsRuntimeFrozenSkillContextResolver(() -> catalog, () -> null, settings, budget), settings);
    }
    OpsLlmTraceContext.Trace trace() {
        return new OpsLlmTraceContext.Trace(events, null, "owner", "node", "AGENT", "planner", "llm", frame);
    }
    <T> T run(Supplier<T> action) { return OpsLlmTraceContext.withTrace(trace(), action); }
    OpsLlmSkillContextService service() { return new OpsLlmSkillContextService(() -> resolver); }
}
