package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.Map;

final class OpsRuntimeToolContributionSupport {

    private OpsRuntimeToolContributionSupport() {
    }

    static String runtimeRunId(OpsAgentChatRequest request) {
        if (request == null) return "";
        if (StringUtils.hasText(request.getRunId())) return request.getRunId().trim();
        OpsAgentRunRequestDTO runRequest = analysisRequest(request);
        return runRequest != null && StringUtils.hasText(runRequest.getRunId())
                ? runRequest.getRunId().trim()
                : "";
    }

    static String runtimeActor(OpsAgentChatRequest request) {
        if (request == null) return "";
        OpsAgentRunRequestDTO runRequest = analysisRequest(request);
        if (runRequest != null && StringUtils.hasText(runRequest.getRequestedBy())) {
            return runRequest.getRequestedBy().trim();
        }
        return stringValue(request.getUserId());
    }

    static OpsAgentRunRequestDTO analysisRequest(OpsAgentChatRequest request) {
        if (request == null) return null;
        Object value = request.getMetadata() == null
                ? null
                : request.getMetadata().get(WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST);
        if (value instanceof OpsAgentRunRequestDTO dto) return dto;
        if (value instanceof Map<?, ?>) {
            try {
                OpsAgentRunRequestDTO dto = JSON.parseObject(
                        JSON.toJSONString(value), OpsAgentRunRequestDTO.class);
                if (dto != null) return dto;
            } catch (RuntimeException ignored) {
                // Fall through to the canonical Chat request projection below.
            }
        }
        if (!StringUtils.hasText(request.getRunId())
                || !StringUtils.hasText(request.getProjectId())) {
            return null;
        }
        return OpsAgentRunRequestDTO.builder()
                .runId(request.getRunId().trim())
                .requestedBy(StringUtils.hasText(request.getUserId())
                        ? request.getUserId().trim()
                        : "ops-agent")
                .projectId(request.getProjectId().trim())
                .agentDefinitionId(request.getAgentDefinitionId())
                .agentVersion(request.getAgentVersion())
                .query(request.getQuery())
                .question(request.getQuery())
                .rangeMinutes(request.getRangeMinutes())
                .promWindow(request.getPromWindow())
                .includeRecentLogs(request.getIncludeRecentLogs())
                .maxRounds(request.getMaxRounds())
                .subAgentMaxIterations(request.getSubAgentMaxIterations())
                .nodeTimeoutSeconds(request.getNodeTimeoutSeconds())
                .maxEvidenceItems(request.getMaxEvidenceItems())
                .changeRequested(request.getChangeRequested())
                .notifyChannel(request.getNotifyChannel())
                .notificationChannelId(request.getNotificationChannelId())
                .notificationTarget(request.getNotificationTarget())
                .executionStyle(request.getMode())
                .build();
    }

    static void warn(OpsRuntimeResourceContext context, String summary) {
        context.record(OpsRuntimeEvent.builder()
                .eventType("RESOURCE_WARN")
                .status("SUCCEEDED")
                .summary(summary)
                .payload(Map.of("owner", context.ownerLabel()))
                .build());
    }

    static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
