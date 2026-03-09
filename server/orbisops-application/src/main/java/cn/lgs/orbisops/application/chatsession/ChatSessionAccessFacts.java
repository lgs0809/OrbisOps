package cn.lgs.orbisops.application.chatsession;

/** Protocol-neutral session access facts resolved by an outer adapter. */
public record ChatSessionAccessFacts(
        boolean exists,
        String ownerId,
        boolean readable,
        boolean writable) {

    public ChatSessionAccessFacts {
        ownerId = ownerId == null ? "" : ownerId.trim();
    }

    public static ChatSessionAccessFacts missing() {
        return new ChatSessionAccessFacts(false, "", false, false);
    }
}
