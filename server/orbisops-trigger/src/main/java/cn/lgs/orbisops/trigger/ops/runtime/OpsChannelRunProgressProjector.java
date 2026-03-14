package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.channel.ChannelRunProgressApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Best-effort Channel projection for durable Work Session events.
 * Runtime authority never depends on progress delivery.
 */
@Slf4j
@Component
public final class OpsChannelRunProgressProjector {

    private final ChannelRunProgressApplicationService progress;
    private final Executor executor;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public OpsChannelRunProgressProjector(
            ChannelRunProgressApplicationService progress,
            @Qualifier("opsSubAgentExecutor") Executor executor) {
        if (progress == null) throw new IllegalArgumentException("CHANNEL_RUN_PROGRESS_SERVICE_REQUIRED");
        if (executor == null) throw new IllegalArgumentException("CHANNEL_RUN_PROGRESS_EXECUTOR_REQUIRED");
        this.progress = progress;
        this.executor = executor;
    }

    public void project(OpsAgentChatRequest request, OpsRuntimeEvent event) {
        Projection projection = projection(request, event);
        if (projection == null) return;
        String runId = value(request.getRunId());
        CompletableFuture<Void> scheduled = tails.compute(runId, (key, previous) -> {
            CompletableFuture<Void> head = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, failure) -> null);
            return head.thenRunAsync(() -> projectSafely(request, event, projection), executor);
        });
        if (projection.terminal()) {
            scheduled.whenComplete((ignored, failure) -> tails.remove(runId, scheduled));
        }
    }

    private void projectSafely(OpsAgentChatRequest request,
                               OpsRuntimeEvent event,
                               Projection projection) {
        try {
            Map<String, Object> metadata = request.getMetadata() == null ? Map.of() : request.getMetadata();
            progress.project(new ChannelRunProgressApplicationService.ProgressCommand(
                    value(request.getProjectId()),
                    value(metadata.get("channelId")),
                    value(metadata.get("externalConversationId")),
                    value(request.getRunId()),
                    value(request.getSessionId()),
                    value(metadata.get("externalMessageId")),
                    projection.stage(),
                    safeSummary(event),
                    projection.force(),
                    "channel-runtime"));
        } catch (RuntimeException failure) {
            log.debug("Channel run progress projection skipped, runId={}, eventType={}, reason={}",
                    value(request == null ? null : request.getRunId()),
                    value(event == null ? null : event.getEventType()),
                    value(failure.getMessage()));
        }
    }

    private Projection projection(OpsAgentChatRequest request, OpsRuntimeEvent event) {
        if (request == null || event == null || request.getMetadata() == null) return null;
        Map<String, Object> metadata = request.getMetadata();
        if (!"CHANNEL".equalsIgnoreCase(value(metadata.get("source")))) return null;
        if (value(request.getRunId()).isBlank()
                || value(request.getProjectId()).isBlank()
                || value(metadata.get("channelId")).isBlank()
                || value(metadata.get("externalConversationId")).isBlank()
                || value(metadata.get("externalMessageId")).isBlank()) return null;

        String type = value(event.getEventType()).toUpperCase(Locale.ROOT);
        if (type.isBlank() || "TEXT_DELTA".equals(type)) return null;
        return switch (type) {
            case "RUN_ACCEPTED" -> new Projection("PREPARING", true, false);
            case "RUNTIME_PLANNED" -> new Projection("PREPARING", false, false);
            case "RUN_STARTED" -> new Projection("INVESTIGATING", true, false);
            case "WORKFLOW_APPROVAL_WAITING" -> new Projection("WAITING_APPROVAL", true, false);
            case "WORKFLOW_APPROVAL_RESUMED" -> new Projection("RESUMING", true, false);
            case "CHANGE_PACKAGE_EVALUATED", "CHANGE_PACKAGE_EVIDENCE_COMPLETION_STARTED",
                    "CHANGE_PACKAGE_EVIDENCE_COMPLETION_FINISHED" ->
                    new Projection("PREPARING_CHANGE", false, false);
            case "FINAL_OUTPUT" -> new Projection("COMPLETED", true, true);
            case "RUN_CANCELED" -> new Projection("CANCELED", true, true);
            case "RUN_FAILED" -> new Projection("FAILED", true, true);
            default -> stageEvent(type) ? new Projection("INVESTIGATING", false, false) : null;
        };
    }

    private boolean stageEvent(String type) {
        return type.startsWith("TOOL_CALL_")
                || type.equals("NODE_START")
                || type.equals("NODE_END")
                || type.equals("NODE_STARTED")
                || type.equals("NODE_FINISHED")
                || type.equals("TYPED_WORKFLOW_NODE_REPLAYED")
                || type.equals("MODEL_CALL_STARTED")
                || type.equals("MODEL_CALL_FINISHED");
    }

    private String safeSummary(OpsRuntimeEvent event) {
        String summary = value(event == null ? null : event.getSummary());
        if (summary.isBlank()) summary = value(event == null ? null : event.getEventType());
        return summary.length() <= 420 ? summary : summary.substring(0, 420) + "...";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Projection(String stage, boolean force, boolean terminal) {
    }
}
