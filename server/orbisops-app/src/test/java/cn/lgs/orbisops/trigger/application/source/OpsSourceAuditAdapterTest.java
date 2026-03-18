package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.source.SourceAuditEvent;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OpsSourceAuditAdapterTest {

    @Test
    void mapsTypedEventToProjectScopedCompatibilityAudit() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsSourceAuditAdapter adapter = new OpsSourceAuditAdapter(audits);
        SourceAuditEvent event = new SourceAuditEvent(
                "project-1", "source-repository", "register", "repo-1", "before", "after");

        adapter.record(event);

        verify(audits).record(
                "project-1", "source-repository", "register", "repo-1", "before", "after");
    }

    @Test
    void mapsGlobalEventAndIgnoresNull() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsSourceAuditAdapter adapter = new OpsSourceAuditAdapter(audits);

        adapter.record(new SourceAuditEvent(
                "", "source-repository", "read", "repo-1", null, "after"));
        verify(audits).record("source-repository", "read", "repo-1", null, "after");

        OpsConfigAuditService untouched = mock(OpsConfigAuditService.class);
        new OpsSourceAuditAdapter(untouched).record(null);
        verifyNoInteractions(untouched);
    }
}
