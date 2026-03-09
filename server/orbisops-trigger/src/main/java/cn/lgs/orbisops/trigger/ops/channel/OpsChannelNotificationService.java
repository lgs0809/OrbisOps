package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.channel.ChannelAnalysisNotificationCommand;
import cn.lgs.orbisops.application.channel.ChannelChatReplyCommand;
import cn.lgs.orbisops.application.channel.ChannelNotificationOutcome;
import cn.lgs.orbisops.application.channel.ChannelNotificationUseCase;
import cn.lgs.orbisops.application.channel.ChannelReplyDeliveryOutcome;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** DTO and administration facade for durable Channel notification workflows. */
@Service
public class OpsChannelNotificationService {

    private final ChannelNotificationUseCase notificationUseCase;
    private final OpsChannelOutboxQueryService queryService;
    private final OpsChannelOutboxManagementService managementService;
    private final OpsChannelOutboxBatchProcessor batchProcessor;

    public OpsChannelNotificationService(
            ChannelNotificationUseCase notificationUseCase,
            OpsChannelOutboxQueryService queryService,
            OpsChannelOutboxManagementService managementService,
            OpsChannelOutboxBatchProcessor batchProcessor) {
        if (notificationUseCase == null) throw new IllegalArgumentException("CHANNEL_NOTIFICATION_USE_CASE_REQUIRED");
        if (queryService == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_QUERY_SERVICE_REQUIRED");
        if (managementService == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_MANAGEMENT_SERVICE_REQUIRED");
        if (batchProcessor == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_BATCH_PROCESSOR_REQUIRED");
        this.notificationUseCase = notificationUseCase;
        this.queryService = queryService;
        this.managementService = managementService;
        this.batchProcessor = batchProcessor;
    }

    public NotifyResult notifyIfNeeded(OpsAgentRunRequestDTO request, OpsAnalysisResponseDTO response) {
        ChannelNotificationOutcome outcome = notificationUseCase.notifyIfNeeded(
                analysisCommand(request, response));
        if (outcome.appendExecutionNote()) {
            addExecutionNote(response, outcome.message());
        }
        return new NotifyResult(outcome.attempted(), outcome.success(), outcome.message());
    }

    public ReplyDelivery enqueueChatReply(
            String projectId,
            String channelId,
            String target,
            String content,
            String runId,
            String sessionId,
            String replyToMessageId) {
        ChannelReplyDeliveryOutcome outcome = notificationUseCase.enqueueChatReply(
                new ChannelChatReplyCommand(
                        projectId,
                        channelId,
                        target,
                        content,
                        runId,
                        sessionId,
                        replyToMessageId));
        return new ReplyDelivery(
                outcome.outboxId(),
                outcome.delivered(),
                outcome.status(),
                outcome.message());
    }

    public Map<String, Object> processPending(int limit) {
        return batchProcessor.process(limit).asMap();
    }

    public List<Map<String, Object>> listOutbox(String projectId, int limit) {
        return queryService.list(projectId, limit);
    }

    public Map<String, Object> status(String projectId) {
        return queryService.status(projectId);
    }

    public Map<String, Object> statusAll() {
        return queryService.statusAll();
    }

    public Map<String, Object> requeueDeadLetter(String projectId, long id, String actor) {
        return managementService.requeue(projectId, id, actor);
    }

    public Map<String, Object> cancelDeadLetter(String projectId, long id, String actor) {
        return managementService.cancel(projectId, id, actor);
    }

    private ChannelAnalysisNotificationCommand analysisCommand(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response) {
        return request == null
                ? null
                : new ChannelAnalysisNotificationCommand(
                        Boolean.TRUE.equals(request.getNotifyChannel()),
                        request.getProjectId(),
                        request.getNotificationChannelId(),
                        request.getNotificationTarget(),
                        request.getRunId(),
                        response == null ? null : response.getAnalysisId(),
                        response == null ? null : response.getGeneratedAt(),
                        response == null ? null : response.getMarkdownReport());
    }

    private void addExecutionNote(OpsAnalysisResponseDTO response, String message) {
        if (response != null
                && response.getExecutionNotes() != null
                && StringUtils.hasText(message)) {
            response.getExecutionNotes().add(message);
        }
    }

    public record NotifyResult(boolean attempted, boolean success, String message) { }

    public record ReplyDelivery(long outboxId, boolean delivered, String status, String message) {
        public boolean terminalFailure() {
            return "DEAD_LETTER".equals(status) || "CANCELLED".equals(status);
        }
    }
}
