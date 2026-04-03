package cn.lgs.orbisops.application.skill;

public interface SkillCanaryReviewModelPort {
    String MODEL = "gpt-5.6-luna";
    String VERSION = "skill-canary-retrospective-v1";
    SkillCanaryReviewPort.Decision review(String frozenInput);
    final class RetryableFailure extends RuntimeException {
        private final long retryAfterMillis;
        public RetryableFailure(String reason, long retryAfterMillis) {
            super(reason); this.retryAfterMillis = Math.max(0, Math.min(86400000, retryAfterMillis));
        }
        public long retryAfterMillis() { return retryAfterMillis; }
    }
}
