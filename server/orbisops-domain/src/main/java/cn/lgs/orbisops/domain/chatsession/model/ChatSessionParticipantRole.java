package cn.lgs.orbisops.domain.chatsession.model;

import java.util.Locale;

/** Participant roles and their write capability. */
public enum ChatSessionParticipantRole {
    OWNER(true),
    EDITOR(true),
    OBSERVER(false);

    private final boolean writable;

    ChatSessionParticipantRole(boolean writable) {
        this.writable = writable;
    }

    public boolean writable() {
        return writable;
    }

    public static ChatSessionParticipantRole requireManaged(String value) {
        ChatSessionParticipantRole role = require(value);
        if (role == OWNER) {
            throw new IllegalArgumentException("participant role 只允许 OBSERVER/EDITOR");
        }
        return role;
    }

    public static ChatSessionParticipantRole require(String value) {
        try {
            return valueOf(text(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("participant role 只允许 OWNER/OBSERVER/EDITOR", error);
        }
    }

    private static String text(String value) {
        return value == null || value.trim().isBlank() ? "OBSERVER" : value.trim();
    }
}
