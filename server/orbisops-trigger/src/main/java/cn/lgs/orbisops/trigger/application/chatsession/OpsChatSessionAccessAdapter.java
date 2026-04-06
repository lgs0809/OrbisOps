package cn.lgs.orbisops.trigger.application.chatsession;

import cn.lgs.orbisops.application.chatsession.ChatSessionAccessFacts;
import cn.lgs.orbisops.application.chatsession.ChatSessionAccessPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;

import java.util.Optional;

/** Trigger adapter resolving session ownership and participant ACL facts. */
public final class OpsChatSessionAccessAdapter implements ChatSessionAccessPort {

    private final OpsChatSessionService sessionService;

    public OpsChatSessionAccessAdapter(OpsChatSessionService sessionService) {
        if (sessionService == null) {
            throw new IllegalArgumentException("CHAT_SESSION_SERVICE_REQUIRED");
        }
        this.sessionService = sessionService;
    }

    @Override
    public ChatSessionAccessFacts facts(String sessionId, String actor) {
        Optional<OpsChatSession> session = sessionService.get(sessionId);
        if (session.isEmpty()) return ChatSessionAccessFacts.missing();
        OpsChatSession value = session.get();
        return new ChatSessionAccessFacts(
                true,
                value.getUserId(),
                sessionService.canRead(sessionId, actor),
                sessionService.canWrite(sessionId, actor));
    }
}
