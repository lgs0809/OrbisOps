package cn.lgs.orbisops.domain.investigation.model;

import java.util.List;
import java.util.Locale;

/** Immutable evidence observation returned by one investigation source. */
public record InvestigationObservation(String source,
                                       String status,
                                       String summary,
                                       List<String> evidence,
                                       List<String> gaps) {

    public InvestigationObservation {
        source = text(source);
        status = upper(status);
        summary = text(summary);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }

    public boolean found() {
        return "FOUND".equals(status);
    }

    public boolean blocked() {
        return "BLOCKED".equals(status);
    }

    public boolean insufficient() {
        return "NOT_FOUND".equals(status) || "INSUFFICIENT".equals(status);
    }

    public String searchableText() {
        return (summary + " " + String.join(" ", evidence) + " " + String.join(" ", gaps))
                .toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String upper(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }
}
