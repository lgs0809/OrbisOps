package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryAuditEvent;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsContextMemoryAuditAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsCreateAndStatusEventsToHistoricalAuditContract() {
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        ObjectProvider<OpsConfigAuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(audit);
        OpsContextMemoryAuditAdapter adapter = new OpsContextMemoryAuditAdapter(provider);
        ContextMemorySnapshot snapshot = snapshot();

        adapter.record(new ContextMemoryAuditEvent("create", snapshot));
        adapter.record(new ContextMemoryAuditEvent("update-status", snapshot));

        verify(audit).recordRuntimeEvent(
                eq("demo-project"), eq(""), eq(""), eq("context-memory"), eq("create"),
                eq("ctx-1"), eq("LOW"), eq("SUCCEEDED"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map
                        && "PROJECT_CONTEXT".equals(map.get("memoryType"))));
        verify(audit).recordRuntimeEvent(
                eq("demo-project"), eq(""), eq("user-1"), eq("context-memory"), eq("update-status"),
                eq("ctx-1"), eq("LOW"), eq("SUCCEEDED"), any());
    }

    private ContextMemorySnapshot snapshot() {
        return new ContextMemorySnapshot(
                1L,
                "ctx-1",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "DDD migration",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                BigDecimal.valueOf(0.9D),
                "manual",
                "session-1",
                "source-hash",
                "user-1",
                "2026-07-21 19:00:00",
                "2026-07-21 20:00:00",
                "");
    }
}
