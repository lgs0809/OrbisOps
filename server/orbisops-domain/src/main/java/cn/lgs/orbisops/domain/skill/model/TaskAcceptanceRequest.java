package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** A reviewer supplies observable acceptance assertions; the server computes the result. */
public record TaskAcceptanceRequest(String requestId, long revision, String goalReview,
        List<Criterion> criteria) {
    /** A bounded complete goal may include both sides of several observation intervals. */
    public static final int MAX_CRITERIA = 64;
    public TaskAcceptanceRequest { criteria = criteria == null ? List.of() : List.copyOf(criteria); }
    public record Criterion(String resultId, String outputHash, String pointer, String operator, Object expected) { }
}
