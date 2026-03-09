package cn.lgs.orbisops.trigger.ops;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Coordinates cooperative cancellation across workflow, sub-agent, and runtime boundaries. */
@Service
public class OpsRunCancellationRegistry {

    private final OpsRunCancellationState state;

    public OpsRunCancellationRegistry() {
        this(OpsRunCancellationSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsRunCancellationRegistry(OpsRunCancellationSettings settings) {
        this(new OpsRunCancellationState(settings, System::currentTimeMillis));
    }

    OpsRunCancellationRegistry(OpsRunCancellationState state) {
        this.state = state;
    }

    public void markCanceled(String runId) {
        String key = requireRunId(runId);
        state.markCanceled(key);
    }

    public boolean isCanceled(String runId) {
        return Thread.currentThread().isInterrupted()
                || (StringUtils.hasText(runId) && state.isCanceled(runId.trim()));
    }

    public void throwIfCanceled(String runId) {
        if (isCanceled(runId)) {
            throw new OpsRunCanceledException(runId);
        }
    }

    /** Compatibility alias retained for callers migrated before the registry API rename. */
    public void assertNotCanceled(String runId) {
        throwIfCanceled(runId);
    }

    public void markFinished(String runId) {
        if (StringUtils.hasText(runId)) {
            state.markFinished(runId.trim());
        }
    }

    public void clear(String runId) {
        if (StringUtils.hasText(runId)) {
            state.clear(runId.trim());
        }
    }

    private String requireRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("RUN_ID_REQUIRED：取消运行时 runId 不能为空");
        }
        return runId.trim();
    }
}
