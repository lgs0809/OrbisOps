package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.analysis.AsyncAnalysisCanceledException;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisCancellationPort;

/** Cooperative cancellation adapter backed by the shared run cancellation registry. */
public final class OpsAsyncAnalysisCancellationAdapter implements AsyncAnalysisCancellationPort {

    private final OpsRunCancellationRegistry registry;

    public OpsAsyncAnalysisCancellationAdapter(OpsRunCancellationRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("OPS_RUN_CANCELLATION_REGISTRY_REQUIRED");
        }
        this.registry = registry;
    }

    @Override
    public void markCanceled(String runId) {
        registry.markCanceled(runId);
    }

    @Override
    public boolean isCanceled(String runId) {
        return registry.isCanceled(runId);
    }

    @Override
    public void assertNotCanceled(String runId) {
        try {
            registry.assertNotCanceled(runId);
        } catch (OpsRunCanceledException error) {
            throw new AsyncAnalysisCanceledException(error.getMessage());
        }
    }

    @Override
    public void markFinished(String runId) {
        registry.markFinished(runId);
    }
}
