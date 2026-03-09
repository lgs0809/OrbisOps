package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Owns request normalization, definition scope resolution and durable cancellation/finish control. */
final class OpsWorkSessionRequestControl {

    private final OpsAgentDefinitionQueryGateway definitionRegistry;
    private final OpsRunCancellationRegistry cancellationRegistry;
    private final OpsWorkSessionRunAdapter workSessionRunService;

    OpsWorkSessionRequestControl(OpsAgentDefinitionQueryGateway definitionRegistry,
                                 OpsRunCancellationRegistry cancellationRegistry,
                                 OpsWorkSessionRunAdapter workSessionRunService) {
        this.definitionRegistry = definitionRegistry;
        this.cancellationRegistry = cancellationRegistry;
        this.workSessionRunService = workSessionRunService;
    }

    OpsAgentChatRequest normalize(OpsAgentChatRequest request) {
        OpsAgentChatRequest normalized = request == null
                ? new OpsAgentChatRequest()
                : request;
        if (normalized.getMetadata() == null) {
            normalized.setMetadata(new LinkedHashMap<>());
        }
        if (!StringUtils.hasText(normalized.getQuery())) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (!StringUtils.hasText(normalized.getSessionId())) {
            normalized.setSessionId("ops-session-" + UUID.randomUUID());
        }
        if (!StringUtils.hasText(normalized.getRunId())) {
            Object metadataRunId = normalized.getMetadata().get("runId");
            if (metadataRunId != null && StringUtils.hasText(String.valueOf(metadataRunId))) {
                normalized.setRunId(String.valueOf(metadataRunId).trim());
            } else {
                normalized.setRunId("chat-"
                        + normalized.getSessionId()
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8));
            }
        }
        normalized.getMetadata().put("runId", normalized.getRunId());
        if (!StringUtils.hasText(normalized.getUserId())) {
            normalized.setUserId("web-user");
        }
        return normalized;
    }

    OpsAgentDefinition resolveDefinition(OpsAgentChatRequest request) {
        if (request.getAgentDefinition() != null
                && StringUtils.hasText(request.getAgentDefinition().getAgentId())) {
            OpsAgentDefinition definition = request.getAgentDefinition();
            assertProjectScope(request, definition);
            return definition;
        }
        return definitionRegistry.resolveForProject(
                request.getAgentDefinitionId(),
                request.getAgentVersion(),
                Boolean.TRUE.equals(request.getPreviewDraft()),
                request.getProjectId());
    }

    void assertNotCanceled(OpsAgentChatRequest request) {
        if (cancellationRegistry != null) {
            cancellationRegistry.assertNotCanceled(request == null ? null : request.getRunId());
        }
        if (request != null
                && request.getMetadata() != null
                && request.getMetadata().containsKey(OpsWorkSessionClaimMetadata.ATTEMPT_ID)
                && workSessionRunService.cancelRequested(
                request.getRunId(), request.getProjectId())) {
            throw new OpsRunCanceledException("Work Session 已收到持久化取消请求");
        }
    }

    void finish(OpsAgentChatRequest request,
                String status,
                String errorMessage) {
        workSessionRunService.finish(request, status, errorMessage);
    }

    void finish(OpsAgentChatRequest request,
                String status,
                String errorMessage,
                OpsAgentChatResponse response) {
        workSessionRunService.finish(request, status, errorMessage, response);
    }

    void markFinished(OpsAgentChatRequest request) {
        if (cancellationRegistry != null) {
            cancellationRegistry.markFinished(request == null ? null : request.getRunId());
        }
    }

    private void assertProjectScope(OpsAgentChatRequest request,
                                    OpsAgentDefinition definition) {
        if (!StringUtils.hasText(request.getProjectId())) {
            throw new IllegalArgumentException("运行 Agent 前必须选择 projectId");
        }
        if (StringUtils.hasText(definition.getProjectId())
                && !request.getProjectId().trim().equals(definition.getProjectId().trim())) {
            throw new IllegalArgumentException("Agent "
                    + definition.getAgentId()
                    + " 属于项目 "
                    + definition.getProjectId()
                    + "，不能在项目 "
                    + request.getProjectId()
                    + " 中运行");
        }
    }
}
