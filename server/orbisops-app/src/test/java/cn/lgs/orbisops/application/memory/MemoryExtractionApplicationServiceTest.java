package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryExtractionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExtractionApplicationServiceTest {

    @Test
    void combinesModelAndRuleDraftsInStableOrderAndAppliesFinalLimit() {
        AtomicInteger receivedMaxInput = new AtomicInteger();
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> {
                    receivedMaxInput.set(maxInputChars);
                    return List.of(new MemoryExtractionDraft(
                            " user_preference ",
                            "模型偏好",
                            BigDecimal.valueOf(2D),
                            "[\"llm\"]",
                            "llm_extractor",
                            "user",
                            "模型标题",
                            "模型摘要",
                            "模型原因"));
                },
                null);

        List<MemoryItemCandidate> result = service.extract(new MemoryExtractionCommand(
                message("记住以后默认先给结论再给依据；demo-project 项目目标是外挂型运维 Agent。"),
                3,
                true,
                4321));

        assertEquals(4321, receivedMaxInput.get());
        assertEquals(List.of("USER_PREFERENCE", "PROJECT_CONTEXT", "USER_WORKFLOW"),
                result.stream().map(MemoryItemCandidate::memoryType).toList());
        assertEquals(BigDecimal.ONE, result.get(0).importance());
        assertEquals("2026-07-21 22:30:00", result.get(0).createdAt());
        assertFalse(result.get(0).sourceMessageHash().isBlank());
    }

    @Test
    void disabledModelSkipsSecondaryPortAndStillRunsRules() {
        AtomicInteger calls = new AtomicInteger();
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> {
                    calls.incrementAndGet();
                    return List.of();
                },
                null);

        List<MemoryItemCandidate> result = service.extract(new MemoryExtractionCommand(
                message("以后优先给表格。"),
                6,
                false,
                4000));

        assertEquals(0, calls.get());
        assertEquals(List.of("USER_PREFERENCE"),
                result.stream().map(MemoryItemCandidate::memoryType).toList());
    }

    @Test
    void modelFailureIsObservedAndRuleFallbackContinues() {
        List<String> failures = new ArrayList<>();
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> {
                    throw new IllegalStateException("model down");
                },
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));

        List<MemoryItemCandidate> result = service.extract(new MemoryExtractionCommand(
                message("以后优先给结论。"),
                6,
                true,
                4000));

        assertEquals(List.of("model-extraction:model down"), failures);
        assertEquals(List.of("USER_PREFERENCE"),
                result.stream().map(MemoryItemCandidate::memoryType).toList());
    }

    @Test
    void failureObserverExceptionDoesNotBlockRuleFallback() {
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> {
                    throw new IllegalStateException("model down");
                },
                (operation, error) -> {
                    throw new IllegalStateException("observer down");
                });

        List<MemoryItemCandidate> result = service.extract(new MemoryExtractionCommand(
                message("以后优先给结论。"),
                6,
                true,
                4000));

        assertEquals(1, result.size());
        assertEquals("USER_PREFERENCE", result.get(0).memoryType());
    }

    @Test
    void unsafeAndInvalidModelDraftsAreFilteredByDomainPolicy() {
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> List.of(
                        new MemoryExtractionDraft(
                                "UNKNOWN", "invalid", null, "[]", "llm", "", "", "", ""),
                        new MemoryExtractionDraft(
                                "USER_PREFERENCE", "记住 traceId=abc", null, "[]", "llm", "", "", "", "")),
                null);

        List<MemoryItemCandidate> result = service.extract(new MemoryExtractionCommand(
                message("普通陈述，不触发规则。"),
                6,
                true,
                4000));

        assertTrue(result.isEmpty());
    }

    @Test
    void modelCannotTurnOneShotOperationalRequestIntoPersistentPreference() {
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> List.of(new MemoryExtractionDraft(
                        "USER_PREFERENCE",
                        "用户偏好只读列出资源和工具，不要生成 ChangePackage",
                        BigDecimal.valueOf(0.99D),
                        "[\"llm\"]",
                        "llm_extractor",
                        "USER",
                        "用户偏好",
                        "只读列资源",
                        "模型错误地将一次性命令判断为稳定偏好")),
                null);

        List<MemoryItemCandidate> result = service.extract(new MemoryExtractionCommand(
                message("请再次只读列出 demo-project 项目当前已授权的资源和工具，只读即可，不要生成 ChangePackage。"),
                6,
                true,
                4000));

        assertTrue(result.isEmpty());
    }

    @Test
    void nullOrBlankInputReturnsEmptyWithoutCallingModel() {
        AtomicInteger calls = new AtomicInteger();
        MemoryExtractionApplicationService service = service(
                (message, maxInputChars) -> {
                    calls.incrementAndGet();
                    return List.of();
                },
                null);

        assertEquals(List.of(), service.extract(null));
        assertEquals(List.of(), service.extract(new MemoryExtractionCommand(null, 6, true, 4000)));
        assertEquals(List.of(), service.extract(new MemoryExtractionCommand(
                message(" "), 6, true, 4000)));
        assertEquals(0, calls.get());
    }

    private MemoryExtractionApplicationService service(MemoryModelExtractionPort modelPort,
                                                       MemoryExtractionFailurePort failurePort) {
        return new MemoryExtractionApplicationService(
                new MemoryExtractionPolicy(),
                new MemoryContentHashPolicy(),
                modelPort,
                failurePort,
                () -> "2026-07-21 22:30:00");
    }

    private ColdMemoryMessageSnapshot message(String content) {
        return new ColdMemoryMessageSnapshot(
                "session-1",
                "user-1",
                "user",
                content,
                "2026-07-21 22:00:00",
                Map.of("projectId", "demo-project", "agentId", "ops"));
    }
}
