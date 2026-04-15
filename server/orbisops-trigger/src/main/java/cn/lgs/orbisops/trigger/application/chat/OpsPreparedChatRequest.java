package cn.lgs.orbisops.trigger.application.chat;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;

/** Request after server-owned identity/project/session/default-Agent preparation. */
public record OpsPreparedChatRequest(OpsAgentChatRequest request) {

    public OpsPreparedChatRequest {
        if (request == null) throw new IllegalArgumentException("聊天请求不能为空");
    }
}
