package cn.lgs.orbisops.trigger.application.episode;

import cn.lgs.orbisops.application.episode.TaskEpisodeStore;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;

/** A separate projection of the original conversation context, never an input to normal memory. */
@Component
public class OpsTaskEpisodeContextCapture {
    private static final Logger LOG = LoggerFactory.getLogger(OpsTaskEpisodeContextCapture.class);
    private final TaskEpisodeStore store;
    public OpsTaskEpisodeContextCapture(TaskEpisodeStore store) { this.store = store; }
    public void capture(OpsAgentChatRequest request, String memoryContext, Map<String, Object> bundle) {
        if (request == null) return;
        try {
            store.captureContext(request.getProjectId(), request.getSessionId(), request.getRunId(), Map.of(
                    "originalUserQuery", request.getQuery() == null ? "" : request.getQuery(),
                    "memoryContext", memoryContext == null ? "" : memoryContext,
                    "contextBundleId", String.valueOf(bundle.getOrDefault("contextBundleId", "")),
                    "contextBundleHash", String.valueOf(bundle.getOrDefault("contextBundleHash", ""))));
        } catch (RuntimeException error) {
            // Replay explicitly marks reconstruction if capture is missing; main work does not wait for classification.
            LOG.warn("Episode context capture degraded: {}", error.getClass().getSimpleName());
        }
    }
}
