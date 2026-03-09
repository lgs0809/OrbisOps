package cn.lgs.orbisops.domain.worksession.run.model;

import java.util.Locale;

public enum WorkSessionParticipantRole {
    OWNER,
    EDITOR,
    OBSERVER;

    public static WorkSessionParticipantRole parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            return OBSERVER;
        }
    }

    public boolean canWrite() {
        return this == OWNER || this == EDITOR;
    }
}
