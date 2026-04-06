package cn.lgs.orbisops.application.chatsession;

/** Application boundary for trusted-entry, participant ACL and owner checks. */
public final class ChatSessionAccessUseCase {

    private final ChatSessionAccessPort accessPort;

    public ChatSessionAccessUseCase(ChatSessionAccessPort accessPort) {
        if (accessPort == null) {
            throw new IllegalArgumentException("CHAT_SESSION_ACCESS_PORT_REQUIRED");
        }
        this.accessPort = accessPort;
    }

    public void assertRead(String sessionId, String actor) {
        if (trustedEntry(sessionId, actor)) return;
        ChatSessionAccessFacts facts = accessPort.facts(normalize(sessionId), normalize(actor));
        if (!facts.readable()) {
            throw new SecurityException("SESSION_READ_FORBIDDEN");
        }
    }

    public void assertWrite(String sessionId, String actor) {
        if (trustedEntry(sessionId, actor)) return;
        ChatSessionAccessFacts facts = accessPort.facts(normalize(sessionId), normalize(actor));
        if (!facts.exists()) return;
        if (!facts.writable()) {
            throw new SecurityException("SESSION_WRITE_FORBIDDEN");
        }
    }

    public void assertOwner(String sessionId, String actor) {
        if (trustedEntry(sessionId, actor)) return;
        String normalizedActor = normalize(actor);
        ChatSessionAccessFacts facts = accessPort.facts(normalize(sessionId), normalizedActor);
        if (!facts.exists()) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!normalizedActor.equals(facts.ownerId())) {
            throw new SecurityException("SESSION_OWNER_REQUIRED");
        }
    }

    private boolean trustedEntry(String sessionId, String actor) {
        return normalize(sessionId).isBlank() || normalize(actor).isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
