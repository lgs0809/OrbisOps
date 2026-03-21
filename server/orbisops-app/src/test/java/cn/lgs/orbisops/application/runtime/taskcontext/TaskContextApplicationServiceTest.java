package cn.lgs.orbisops.application.runtime.taskcontext;

import cn.lgs.orbisops.domain.runtime.taskcontext.adapter.repository.ITaskContextRepository;
import cn.lgs.orbisops.domain.runtime.taskcontext.exception.TaskContextVersionConflictException;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextContent;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextEventFact;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextState;
import cn.lgs.orbisops.domain.runtime.taskcontext.service.TaskContextPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContextApplicationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 9, 0);

    @Test
    void startCreatesDefaultOperationalContextAndAuditsAfterSave() {
        InMemoryRepository repository = new InMemoryRepository();
        List<TaskContextSnapshot> audited = new ArrayList<>();
        TaskContextCommandApplicationService service = service(repository, audited);

        TaskContextSnapshot saved = service.start(new TaskContextStartCommand(
                        "run-1", "session-1", "project-1", "agent-1",
                        "排查订单延迟", List.of("mysql", "logs")))
                .orElseThrow();

        assertEquals(TaskContextState.RUNNING, saved.state());
        assertEquals("排查订单延迟", saved.content().goal());
        assertEquals(List.of("理解问题", "收集证据", "生成结论"),
                saved.content().pendingActions());
        assertEquals(List.of("mysql", "logs"), saved.content().usedSkills());
        assertEquals(1, saved.version());
        assertEquals(List.of(saved), audited);
    }

    @Test
    void progressDerivesFactsRuledOutActionsAndToolSummary() {
        InMemoryRepository repository = new InMemoryRepository();
        TaskContextCommandApplicationService service = service(repository, new ArrayList<>());
        service.start(new TaskContextStartCommand(
                "run-1", "session-1", "project-1", "agent-1", "goal", List.of()));

        TaskContextSnapshot saved = service.progress(new TaskContextProgressCommand(
                        "run-1", "session-1", "project-1", "agent-1", "goal",
                        List.of("mysql"), "RUNNING",
                        List.of(
                                new TaskContextEventFact(
                                        "TOOL_RESULT", "SUCCEEDED", "查询慢 SQL", "发现 orders 索引缺失"),
                                new TaskContextEventFact(
                                        "SEARCH_RESULT", "NOT_FOUND", "未发现缓存异常", "")),
                        "继续分析"))
                .orElseThrow();

        assertEquals(List.of("发现 orders 索引缺失"), saved.content().knownFacts());
        assertEquals(List.of("未发现缓存异常"), saved.content().ruledOut());
        assertEquals(List.of("查询慢 SQL"), saved.content().completedActions());
        assertEquals(List.of("查询慢 SQL"), saved.content().lastToolResultsSummary());
        assertEquals(List.of("继续收集证据", "补齐结论", "生成最终报告"),
                saved.content().pendingActions());
        assertEquals(2, saved.version());
    }

    @Test
    void finishMapsTerminalStateAndPreservesLegacyAbbreviationLength() {
        InMemoryRepository repository = new InMemoryRepository();
        TaskContextCommandApplicationService service = service(repository, new ArrayList<>());
        String output = "x".repeat(1200);

        TaskContextSnapshot saved = service.finish(new TaskContextFinishCommand(
                        "run-1", "session-1", "project-1", "agent-1", "goal",
                        List.of(), "FAILED", output, List.of()))
                .orElseThrow();

        assertEquals(TaskContextState.FAILED, saved.state());
        assertEquals(List.of(), saved.content().pendingActions());
        assertEquals(List.of("任务未成功结束，需要查看 run trace 和最近错误。"),
                saved.content().openQuestions());
        assertEquals(1003, saved.summary().length());
        assertTrue(saved.summary().endsWith("..."));
    }

    @Test
    void unavailableStoreKeepsWriteBestEffortAndDoesNotAudit() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.unavailable = true;
        List<TaskContextSnapshot> audited = new ArrayList<>();
        TaskContextCommandApplicationService service = service(repository, audited);

        Optional<TaskContextSnapshot> saved = service.start(new TaskContextStartCommand(
                "run-1", "session-1", "project-1", "agent-1", "goal", List.of()));

        assertTrue(saved.isEmpty());
        assertTrue(audited.isEmpty());
    }

    @Test
    void versionConflictReloadsAndRetriesOnce() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.data = snapshot(1, "initial");
        repository.conflictOnce = true;
        List<TaskContextSnapshot> audited = new ArrayList<>();
        TaskContextCommandApplicationService service = service(repository, audited);

        TaskContextSnapshot saved = service.progress(new TaskContextProgressCommand(
                        "run-1", "session-1", "project-1", "agent-1", "goal",
                        List.of(), "RUNNING", List.of(), "after retry"))
                .orElseThrow();

        assertEquals(3, saved.version());
        assertEquals("after retry", saved.summary());
        assertEquals(1, audited.size());
    }

    @Test
    void failedSaveDoesNotAuditOrPublishFalseSuccess() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.failSave = true;
        List<TaskContextSnapshot> audited = new ArrayList<>();
        TaskContextCommandApplicationService service = service(repository, audited);

        Optional<TaskContextSnapshot> saved = service.start(new TaskContextStartCommand(
                "run-1", "session-1", "project-1", "agent-1", "goal", List.of()));

        assertFalse(saved.isPresent());
        assertTrue(audited.isEmpty());
    }

    private TaskContextCommandApplicationService service(
            InMemoryRepository repository,
            List<TaskContextSnapshot> audited) {
        TaskContextAuditPort audit = (before, after) -> audited.add(after);
        return new TaskContextCommandApplicationService(
                repository, audit, new TaskContextPolicy(), () -> NOW);
    }

    private static TaskContextSnapshot snapshot(int version, String summary) {
        return new TaskContextSnapshot(
                1L,
                "run-1",
                "session-1",
                "project-1",
                "agent-1",
                TaskContextState.RUNNING,
                TaskContextContent.empty(),
                summary,
                null,
                version,
                NOW.minusMinutes(1),
                NOW.minusMinutes(1));
    }

    private static final class InMemoryRepository implements ITaskContextRepository {
        private TaskContextSnapshot data;
        private boolean unavailable;
        private boolean failSave;
        private boolean conflictOnce;

        @Override
        public Optional<TaskContextSnapshot> find(String runId) {
            if (unavailable) throw new IllegalStateException("database unavailable");
            return Optional.ofNullable(data);
        }

        @Override
        public Optional<TaskContextSnapshot> trySave(
                TaskContextSnapshot snapshot,
                int expectedVersion) {
            if (failSave) return Optional.empty();
            int currentVersion = data == null ? 0 : data.version();
            if (expectedVersion != currentVersion) {
                throw new TaskContextVersionConflictException(snapshot.runId(), expectedVersion);
            }
            if (conflictOnce) {
                conflictOnce = false;
                data = copy(snapshot, expectedVersion + 1, "concurrent update");
                throw new TaskContextVersionConflictException(snapshot.runId(), expectedVersion);
            }
            data = copy(snapshot, expectedVersion + 1, snapshot.summary());
            return Optional.of(data);
        }

        private TaskContextSnapshot copy(
                TaskContextSnapshot source,
                int version,
                String summary) {
            return new TaskContextSnapshot(
                    data == null ? 1L : data.persistenceId(),
                    source.runId(),
                    source.sessionId(),
                    source.projectId(),
                    source.agentId(),
                    source.state(),
                    source.content(),
                    summary,
                    source.lastEventId(),
                    version,
                    data == null ? source.createdAt() : data.createdAt(),
                    source.updatedAt());
        }
    }
}
