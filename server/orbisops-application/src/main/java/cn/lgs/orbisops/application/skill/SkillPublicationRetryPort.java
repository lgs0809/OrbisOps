package cn.lgs.orbisops.application.skill;

import java.util.Map;
import java.util.Optional;

/** Durable retries of an existing immutable candidate; never repeats candidate authoring. */
public interface SkillPublicationRetryPort {
    Map<String,Object> enqueue(String projectId, String candidateId);
    Map<String,Object> status(String projectId, String candidateId);
    Optional<Claim> claim();
    void requireCurrent(Claim claim);
    void complete(Claim claim, SkillReleaseStartOutcome outcome);
    void defer(Claim claim, String reason);
    record Claim(String candidateId, String token, int attempt) { }
}
