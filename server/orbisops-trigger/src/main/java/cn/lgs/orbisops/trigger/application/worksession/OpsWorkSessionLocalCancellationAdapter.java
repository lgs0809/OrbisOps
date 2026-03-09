package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.run.WorkSessionLocalCancellationPort;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;

/** Trigger adapter for the in-process cancellation registry. */
public final class OpsWorkSessionLocalCancellationAdapter
        implements WorkSessionLocalCancellationPort {

    private final OpsRunCancellationRegistry registry;

    public OpsWorkSessionLocalCancellationAdapter(OpsRunCancellationRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("WORK_SESSION_CANCELLATION_REGISTRY_REQUIRED");
        }
        this.registry = registry;
    }

    @Override
    public void markCanceled(String runId) {
        registry.markCanceled(runId);
    }
}
