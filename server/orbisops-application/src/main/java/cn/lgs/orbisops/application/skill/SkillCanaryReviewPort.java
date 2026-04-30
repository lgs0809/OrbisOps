package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Optional;

/** Durable post-run review. Neither classification nor model availability is a chat dependency. */
public interface SkillCanaryReviewPort {
    int discover(int limit);
    Optional<Claim> claim(long now);
    void complete(Claim claim, Decision decision);
    void retry(Claim claim, long dueAt, String reason);

    record Claim(String id, String lease, String input, int attempt) { }
    record Decision(String safety, String attribution, List<String> evidenceIds, String reason) {
        public Decision {
            if (!List.of("SAFE", "VIOLATION", "UNKNOWN").contains(safety)
                    || !List.of("NONE", "CANDIDATE", "ENVIRONMENT", "UNKNOWN").contains(attribution)
                    || evidenceIds == null || evidenceIds.size() > 20 || reason == null
                    || reason.isBlank() || reason.length() > 2000)
                throw new IllegalArgumentException("CANARY_REVIEW_INVALID");
            evidenceIds = List.copyOf(evidenceIds);
            if (evidenceIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 200)
                    || (("VIOLATION".equals(safety) || !List.of("NONE", "UNKNOWN").contains(attribution))
                        && evidenceIds.isEmpty()))
                throw new IllegalArgumentException("CANARY_REVIEW_EVIDENCE_REQUIRED");
        }
        public boolean conclusive() { return !"UNKNOWN".equals(safety) && !"UNKNOWN".equals(attribution); }
    }
}
