package cn.lgs.orbisops.application.skill;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SkillMaintenancePort {
    record Subject(long id, String projectId, String skillId, int version, String skillHash,
            String packageHash, String content, Instant createdAt, Instant lastUseAt,
            int patches, int checkedPatches, boolean automatic) { }
    record Claim(String id, String token, int attempt, Subject subject) { }
    List<Subject> scan(long afterId, int limit);
    void enqueue(Subject subject, String kind, int tokens, String tokenizer, String reason);
    Optional<Claim> claim();
    void requireCurrent(Claim claim);
    void finish(Claim claim, String status, String reason, Map<String,Object> evidence, int publishedVersion);
    void defer(Claim claim, String reason);
    List<Map<String,Object>> list(String projectId, int limit);
    Map<String,Object> acknowledge(String projectId, String id, String actor, String reason);
}
