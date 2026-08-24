package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillCanaryCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;
import cn.lgs.orbisops.domain.skill.service.SkillCanaryApplicabilityPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillCanarySelectionPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillCanaryApplicabilityTest {
    private final SkillCanaryApplicabilityPolicy policy = new SkillCanaryApplicabilityPolicy();

    @Test void applicableRequestUsesDeclaredPositiveBoundary() {
        assertTrue(policy.score("project-a", "agent-a", "请诊断 Redis 缓存延迟", candidate("redis", "Redis 缓存延迟", "MySQL 死锁")) > 0);
    }
    @Test void unrelatedAndExcludedRequestsCannotReceiveCandidate() {
        var candidate = candidate("redis", "Redis 缓存延迟", "Redis 数据清空");
        assertEquals(0, policy.score("project-a", "agent-a", "请分析磁盘空间", candidate));
        assertEquals(0, policy.score("project-a", "agent-a", "Redis 数据清空", candidate));
        assertEquals(0, policy.score("project-a", "agent-a", "", candidate));
    }
    @Test void projectAndAgentBoundariesAreNotGrantedByTextMatch() {
        var candidate = candidate("redis", "Redis 缓存延迟", "MySQL 死锁");
        assertEquals(0, policy.score("project-b", "agent-a", "Redis 缓存延迟", candidate));
        assertEquals(0, policy.score("project-a", "agent-b", "Redis 缓存延迟", candidate));
    }
    @Test void missingMalformedOrAmbiguousProfilesFailClosed() {
        for (String changes : List.of("[]", "invalid", "{}", "[null]",
                "[{\"section\":\"routingProfile\",\"value\":null}]",
                "[{\"section\":\"routingProfile\",\"value\":{\"whenToUse\":[\"Redis\"]}}]")) {
            assertEquals(0, policy.score("project-a", "agent-a", "Redis", raw("invalid", changes)));
        }
        var broad = candidate("broad", "所有任务", "MySQL 死锁");
        assertEquals(0, policy.score("project-a", "agent-a", "所有任务", broad));
        var repeated = candidate("twice", "Redis", "MySQL").changesJson();
        assertEquals(0, policy.score("project-a", "agent-a", "Redis", raw("twice", repeated.substring(0, repeated.length() - 1) + "," + repeated.substring(1))));
    }
    @Test void aRecentUnrelatedReleaseMustNotHideAnOlderApplicableCandidate() {
        var releases = mock(SkillReleasePort.class);
        var service = new SkillCanaryContextApplicationService(releases,
                new SkillCanaryApplicationService(new SkillCanarySelectionPolicy(), new SkillCanarySettings(true, 100)));
        var redis = candidate("redis", "Redis 缓存延迟", "MySQL 死锁");
        when(releases.findCanaryCandidates("project-a", "agent-a", 20)).thenReturn(List.of(
                candidate("disk", "磁盘空间", "MySQL 死锁"), candidate("mail", "发送邮件", "MySQL 死锁"),
                candidate("cpu", "CPU 使用率", "MySQL 死锁"), redis));
        assertEquals(List.of(redis), service.resolveCandidates("project-a", "agent-a", "run-1", "Redis 缓存延迟"));
        assertTrue(service.resolveCandidates("project-a", "agent-a", "run-1").isEmpty());
        assertTrue(service.resolveCandidates("project-a", "agent-a", "run-1", "MySQL 死锁").isEmpty());
    }
    @Test void disabledCohortDoesNotLoadCandidates() {
        var releases = mock(SkillReleasePort.class);
        var service = new SkillCanaryContextApplicationService(releases,
                new SkillCanaryApplicationService(new SkillCanarySelectionPolicy(), new SkillCanarySettings(false, 10)));
        assertTrue(service.resolveCandidates("project-a", "agent-a", "run-1", "Redis 缓存延迟").isEmpty());
        verifyNoInteractions(releases);
    }
    private SkillCanaryCandidateSnapshot candidate(String id, String positive, String negative) {
        return raw(id, CanonicalJson.stringify(List.of(Map.of("section", "routingProfile", "value", Map.of(
                "category", "OBSERVABILITY", "whenToUse", List.of(positive), "whenNotToUse", List.of(negative), "keywords", List.of())))));
    }
    private SkillCanaryCandidateSnapshot raw(String id, String changes) {
        return new SkillCanaryCandidateSnapshot(id, "candidate-" + id, "project-a", "agent-a", "", SkillReleaseStatus.CANARY,
                "hash-" + id, 0, "", "CREATE", changes, "[]");
    }
}
