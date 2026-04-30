package cn.lgs.orbisops.application.skill;

import java.time.Clock;

/** Five persisted physical attempts, no sleeping or retrying inside the model client. */
public final class SkillCanaryReviewApplicationService {
    private final SkillCanaryReviewPort store;
    private final SkillCanaryReviewModelPort model;
    private final Clock clock;
    public SkillCanaryReviewApplicationService(SkillCanaryReviewPort store, SkillCanaryReviewModelPort model, Clock clock) {
        this.store = java.util.Objects.requireNonNull(store);
        this.model = java.util.Objects.requireNonNull(model);
        this.clock = java.util.Objects.requireNonNull(clock);
    }
    public void replay(int limit) {
        store.discover(100);
        for (int i=0; i<Math.min(Math.max(limit,0),4); i++) {
            var pending = store.claim(clock.millis());
            if (pending.isEmpty()) return;
            var claim = pending.get();
            try { store.complete(claim, model.review(claim.input())); }
            catch (RuntimeException error) {
                long delay = 30_000L << Math.min(3, Math.max(0, claim.attempt()-1));
                if (error instanceof SkillCanaryReviewModelPort.RetryableFailure retry)
                    delay = Math.max(delay, retry.retryAfterMillis());
                store.retry(claim, clock.millis()+delay, error.getClass().getSimpleName());
            }
        }
    }
}
