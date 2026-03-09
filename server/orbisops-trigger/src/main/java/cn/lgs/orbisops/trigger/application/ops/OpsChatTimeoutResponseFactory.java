package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;

import java.util.List;
import java.util.Map;

/** Projects a synchronous timeout into the durable Work Session resume response. */
final class OpsChatTimeoutResponseFactory {

    private static final String GENERIC_REACT_MODE = "AGENT";

    private final OpsChatRuntimeSettings settings;

    OpsChatTimeoutResponseFactory(OpsChatRuntimeSettings settings) {
        this.settings = settings == null ? OpsChatRuntimeSettings.defaults() : settings;
    }

    OpsAgentChatResponse create(OpsAgentChatRequest request) {
        String runId = value(request.getRunId(), "");
        OpsRuntimeEvent timeout = OpsRuntimeEvent.builder()
                .eventType("WORK_SESSION_TIMEOUT")
                .status("TIMEOUT")
                .summary("同步请求已超时，Work Session 仍在后台执行。")
                .payload(Map.of(
                        "timeoutSeconds", settings.syncTimeoutSeconds(),
                        "runId", runId))
                .build();
        OpsRuntimeEvent done = OpsRuntimeEvent.of(
                "DONE",
                "TIMEOUT",
                "同步 Work Session 已超时返回。");
        return OpsAgentChatResponse.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .agentId(value(request.getAgentDefinitionId(), ""))
                .mode(value(request.getMode(), GENERIC_REACT_MODE))
                .engine("SYNC_TIMEOUT_GUARD")
                .content("同步请求超过 " + settings.syncTimeoutSeconds()
                        + " 秒仍未完成。Work Session 没有被取消，可通过 runId 查看进度和结果。")
                .events(List.of(timeout, done))
                .metadata(Map.of(
                        "runId", runId,
                        "status", "TIMEOUT",
                        "timeoutSeconds", settings.syncTimeoutSeconds(),
                        "agenticWorkSessionStarted", true))
                .build();
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
