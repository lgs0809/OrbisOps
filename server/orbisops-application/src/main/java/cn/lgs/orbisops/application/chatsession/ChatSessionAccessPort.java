package cn.lgs.orbisops.application.chatsession;

/** Outbound access fact boundary for Chat Session authorization. */
public interface ChatSessionAccessPort {

    ChatSessionAccessFacts facts(String sessionId, String actor);
}
