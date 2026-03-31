package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionCheckpointPort;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsToolExecutionCheckpointAdapter implements ToolExecutionCheckpointPort {

    private final ObjectProvider<OpsWorkSessionRunAdapter> workSessions;
    private final ObjectProvider<LandingOperationJournalApplicationService> landingJournal;

    public OpsToolExecutionCheckpointAdapter(ObjectProvider<OpsWorkSessionRunAdapter> workSessions) {
        this(workSessions, null);
    }

    @Autowired
    public OpsToolExecutionCheckpointAdapter(
            ObjectProvider<OpsWorkSessionRunAdapter> workSessions,
            ObjectProvider<LandingOperationJournalApplicationService> landingJournal) {
        this.workSessions = workSessions;
        this.landingJournal = landingJournal;
    }

    @Override
    public void checkpoint(
            ToolExecutionRequest request,
            String checkpointType,
            Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        projectLandingCompletion(request, checkpointType, safePayload);
        Map<String, Object> metadata = objectMap(request.requestContext().get("metadata"));
        if (text(metadata.get("_workSessionAttemptId")).isBlank()) return;
        OpsWorkSessionRunAdapter adapter = workSessions.getIfAvailable();
        if (adapter == null) throw new IllegalStateException("WORK_SESSION_CHECKPOINT_SERVICE_UNAVAILABLE");
        Map<String, Object> envelope = new LinkedHashMap<>(request.requestContext());
        envelope.put("projectId", request.projectId());
        envelope.put("runId", request.runId());
        envelope.put("sessionId", request.sessionId());
        envelope.put("userId", request.userId());
        adapter.checkpointToolExecution(envelope, checkpointType, safePayload);
    }

    private void projectLandingCompletion(
            ToolExecutionRequest request,
            String checkpointType,
            Map<String, Object> payload) {
        if (request == null || !ToolExecutionScope.APPROVED_LANDING.equals(request.scope())) return;
        if (!"TOOL_EXECUTION_COMPLETED".equals(checkpointType)
                && !"TOOL_EXECUTION_REUSED".equals(checkpointType)) return;
        LandingOperationJournalApplicationService journal =
                landingJournal == null ? null : landingJournal.getIfAvailable();
        if (journal == null) {
            throw new IllegalStateException("LANDING_OPERATION_JOURNAL_SERVICE_UNAVAILABLE");
        }
        String operationId = text(request.landingContext().get("operationId"));
        if (operationId.isBlank()) {
            java.util.List<String> candidates = journal.operationIdsForTool(
                    request.runId(), request.toolsetId(), request.toolName());
            if (candidates.isEmpty()) {
                // LANDING may use read-only/auxiliary project tools that are not frozen production operations.
                // Their ToolResult/Evidence remains durable, but they do not belong in the ChangePackage journal.
                return;
            }
            if (candidates.size() != 1) {
                throw new IllegalStateException(
                        "LANDING_TOOL_EXECUTION_OPERATION_BINDING_AMBIGUOUS：tool=" + request.toolName());
            }
            operationId = candidates.get(0);
        }
        String executionKey = text(request.requestContext().get("idempotencyKey"));
        String resultId = text(payload.get("resultId"));
        String outputHash = text(payload.get("outputHash"));
        if (executionKey.isBlank() || resultId.isBlank() || outputHash.isBlank()) {
            throw new IllegalStateException("LANDING_TOOL_EXECUTION_COMPLETION_IDENTITY_REQUIRED");
        }
        Map<String, Object> result = new LinkedHashMap<>(payload);
        result.put("executionKey", executionKey);
        journal.completeFromToolExecution(
                request.runId(), operationId, request.toolName(), executionKey, journal.payload(result));
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
