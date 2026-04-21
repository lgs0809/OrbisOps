package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeToolResultPort;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolOutputBudget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsControlledCodeToolResultAdapter implements ControlledCodeToolResultPort {

    private final OpsToolResultStore store;

    public OpsControlledCodeToolResultAdapter(ObjectProvider<OpsToolResultStore> provider) {
        this.store = provider.getIfAvailable();
    }

    @Override
    public boolean available() {
        return store != null;
    }

    @Override
    public StoredToolResult record(ToolResultRequest request) {
        if (store == null) throw new IllegalStateException("TOOL_RESULT_STORE_UNAVAILABLE");
        Map<String, Object> result = store.record(
                request.projectId(), request.sessionId(), request.runId(), request.userId(),
                request.toolsetId(), request.toolName(), request.source(), request.query(), request.output(),
                OpsToolOutputBudget.builder()
                        .maxBytes(request.maxBytes())
                        .maxLines(request.maxLines())
                        .build(),
                request.actor());
        return new StoredToolResult(
                text(result.get("resultId")),
                Boolean.TRUE.equals(result.get("truncated")),
                text(result.get("fullOutputRef")),
                text(result.get("outputHash")));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
