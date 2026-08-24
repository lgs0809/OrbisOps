package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpsLlmSkillContextServiceTest {
    @Test void untrustedNamesCannotUseTheLegacyFilesystemProvider() {
        OpsLlmSkillContextService service = new OpsLlmSkillContextService(() -> null);
        assertEquals("system", service.withLazyContext("system", List.of("a"), false, 100));
        assertEquals("system", service.withEagerContext("system", List.of(), 100));
        assertThrows(OpsLlmSkillContextException.class, () -> service.withEagerContext("system", List.of("a"), 100));
        assertThrows(OpsLlmSkillContextException.class, () -> service.skillTool(List.of("a")));
    }
    @Test void lazyAndEagerResolveExactVersionAndUseTheSameRunBudget() {
        var f = new OpsLlmFrozenSkillTestFixture("a", "Frozen v7. Do not bypass approval.");
        f.run(() -> {
            String first = f.service().withLazyContext("system", List.of("a"), true, 5);
            String retry = f.service().withEagerContext("system", List.of("a"), 100000);
            assertEquals(first, retry);
            assertTrue(first.contains("Do not bypass approval."));
            return null;
        });
        verify(f.catalog, times(2)).getRuntimeSkillVersion("p", "a", 7, "frozen-hash", "package-hash", "PROJECT");
        verify(f.budget, times(2)).reserve(eq("p"), eq("run"), argThat(loads -> loads.size() == 1));
        assertEquals(2, f.events.size());
        assertEquals(7, ((Map<?,?>)f.events.get(0).getPayload().get("skillRef")).get("version"));
    }
    @Test void revokedVersionAndBudgetFailurePropagateWithoutSuccessfulLoadAudit() {
        var f = new OpsLlmFrozenSkillTestFixture("a", "body");
        when(f.catalog.getRuntimeSkillVersion("p", "a", 7, "frozen-hash", "package-hash", "PROJECT"))
                .thenThrow(new SecurityException("revoked"));
        OpsLlmSkillContextException error = assertThrows(OpsLlmSkillContextException.class,
                () -> f.run(() -> f.service().withEagerContext("system", List.of("a"), 6000)));
        assertInstanceOf(SecurityException.class, error.getCause());
        assertTrue(f.events.isEmpty()); verifyNoInteractions(f.budget);
        var b = new OpsLlmFrozenSkillTestFixture("a", "body");
        doThrow(new IllegalStateException("budget exceeded")).when(b.budget).reserve(anyString(), anyString(), anyList());
        assertThrows(OpsLlmSkillContextException.class,
                () -> b.run(() -> b.service().withLazyContext("system", List.of("a"), true, 6000)));
        assertTrue(b.events.isEmpty());
    }
    @Test void toolUsesFrameProjectAndRunAndRechecksCurrentCatalog() {
        OpsRuntimeSkillResolver resolver = mock(OpsRuntimeSkillResolver.class);
        when(resolver.catalogTool(any())).thenReturn(Optional.empty());
        var f = new OpsLlmFrozenSkillTestFixture("a", "body");
        f.run(() -> new OpsLlmSkillContextService(() -> resolver).skillTool(List.of("attacker-name")));
        verify(resolver).catalogTool(argThat(ctx -> "p".equals(ctx.getProjectId()) && "run".equals(ctx.getRequest().getRunId())
                && ctx.getRequest().getMetadata().get("usedSkillVersionRefs").equals(f.frame.selectedRefs())));
    }
    @Test void immutableFrameSurvivesNestedAndWorkerThreadCallsWithoutLeaking() throws Exception {
        var f = new OpsLlmFrozenSkillTestFixture("a", "body");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var trace = f.trace();
            assertSame(f.frame, trace.child("child", "n", "AGENT", "a", "source").skillFrame());
            String body = executor.submit(OpsLlmTraceContext.wrap(trace,
                    () -> f.service().withEagerContext("system", List.of("a"), 6000))::get).get();
            assertTrue(body.contains("body"));
            assertNull(executor.submit(OpsLlmTraceContext::current).get());
            assertNull(OpsLlmTraceContext.current());
            assertThrows(UnsupportedOperationException.class, () -> f.frame.selectedRefs().get(0).put("version", 8));
        } finally { executor.shutdownNow(); }
    }
    @Test void normalizedNamesRemainImmutable() {
        var service = new OpsLlmSkillContextService(() -> null);
        var names = service.normalizedNames(Arrays.asList(" b ", "", "a", "b", null));
        assertEquals(List.of("b", "a"), names);
        assertThrows(UnsupportedOperationException.class, () -> names.add("c"));
    }
}
