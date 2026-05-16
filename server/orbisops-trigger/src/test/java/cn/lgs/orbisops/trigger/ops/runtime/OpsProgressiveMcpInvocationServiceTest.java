package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpsProgressiveMcpInvocationServiceTest {
    @Test void concurrentReadAndWriteMustKeepTheirReviewedAuthoritySeparate() throws Exception {
        var callbackPolicy = mock(OpsMcpCallbackPolicyAdapter.class);
        when(callbackPolicy.matchesTool(anyList(), anyString())).thenAnswer(call ->
                ((List<?>) call.getArgument(0)).contains(call.getArgument(1)));
        var policy = mock(OpsMcpRemoteCallPolicy.class);
        var runtime = mock(OpsMcpProgressiveRuntimeAdapter.class);
        when(runtime.selectAndHydrate(any(), anyString())).thenAnswer(call -> Map.of("toolName", call.getArgument(1)));
        when(policy.assess(any(), anyString(), anyMap())).thenAnswer(call -> {
            var result = mock(OpsMcpRemoteCallAssessment.class);
            when(result.readOnly()).thenReturn("read".equals(call.getArgument(1)));
            when(result.auditMetadata()).thenReturn(Map.of());
            return result;
        });
        var service = new OpsProgressiveMcpInvocationService(callbackPolicy, policy, runtime, mock(OpsMcpRuntimeInvoker.class));
        var shared = OpsMcpServerConfig.builder().allowedTools(List.of("read", "write")).build();
        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new java.util.ArrayList<>();
            for (String tool : List.of("read", "write")) {
                results.add(pool.submit(() -> service.invoke(shared, "{\"toolName\":\"" + tool + "\"}", (verified, name, args) -> {
                    try { barrier.await(3, TimeUnit.SECONDS); }
                    catch (Exception error) { throw new IllegalStateException(error); }
                    assertNotSame(shared, verified);
                    assertEquals(name.equals("read"), verified.getVerifiedReadOnly());
                    assertEquals(name, verified.getVerifiedToolSchema().get("toolName"));
                    return name;
                })));
            }
            assertEquals("read", results.get(0).get(5, TimeUnit.SECONDS));
            assertEquals("write", results.get(1).get(5, TimeUnit.SECONDS));
            assertNull(shared.getVerifiedReadOnly());
            assertNull(shared.getVerifiedToolSchema());
        } finally { pool.shutdownNow(); }
    }
}
