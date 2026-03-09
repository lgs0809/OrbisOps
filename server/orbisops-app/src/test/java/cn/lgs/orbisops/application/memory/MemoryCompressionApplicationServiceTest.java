package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemoryCompressionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MemoryCompressionApplicationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void modelSummaryBuildsProjectionAndExecutesHotBeforeCold() {
        AtomicInteger modelMaxInput = new AtomicInteger();
        AtomicReference<List<ColdMemoryMessageSnapshot>> replacement = new AtomicReference<>();
        List<String> effects = new ArrayList<>();
        MemoryModelSummaryPort modelPort = (messages, maxInputChars) -> {
            modelMaxInput.set(maxInputChars);
            assertEquals(2, messages.size());
            return "模型压缩摘要";
        };
        HotMemoryReplacePort hotPort = (sessionId, messages, maxMessages) -> {
            effects.add("hot");
            assertEquals("session-1", sessionId);
            assertEquals(20, maxMessages);
            replacement.set(messages);
        };
        ColdMemoryStoreApplicationService coldStore = mock(ColdMemoryStoreApplicationService.class);
        doAnswer(invocation -> {
            effects.add("cold");
            return null;
        }).when(coldStore).saveItems(anyList());
        MemoryCompressionApplicationService service = service(modelPort, hotPort, coldStore);

        boolean compressed = service.compress(command(messages(4), true, 4567, 4, 2));

        assertTrue(compressed);
        assertEquals(4567, modelMaxInput.get());
        assertEquals(List.of("hot", "cold"), effects);
        assertEquals(3, replacement.get().size());
        assertEquals("system", replacement.get().get(0).role());
        assertEquals("模型压缩摘要", replacement.get().get(0).content());
        assertEquals("content-2", replacement.get().get(1).content());
        ArgumentCaptor<List<ColdMemoryItemSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(coldStore).saveItems(captor.capture());
        ColdMemoryItemSnapshot item = captor.getValue().get(0);
        assertEquals("summary", item.memoryType());
        assertEquals("llm_context_compressor", item.metadata().get("source"));
        assertEquals(2, item.metadata().get("compressedMessages"));
        assertEquals("2026-07-21 23:00:00", item.createdAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelFailureFallsBackToRuleSummaryAndStillPersists() {
        AtomicReference<List<ColdMemoryMessageSnapshot>> replacement = new AtomicReference<>();
        MemoryCompressionApplicationService service = service(
                (messages, maxInputChars) -> {
                    throw new IllegalStateException("model down");
                },
                (sessionId, messages, maxMessages) -> replacement.set(messages),
                mock(ColdMemoryStoreApplicationService.class));

        boolean compressed = service.compress(command(messages(4), true, 4000, 4, 2));

        assertTrue(compressed);
        assertTrue(replacement.get().get(0).content().contains("会话压缩摘要：共压缩 2 条历史消息"));
    }

    @Test
    void disabledModelUsesRulesWithoutCallingSecondaryPort() {
        AtomicInteger modelCalls = new AtomicInteger();
        MemoryCompressionApplicationService service = service(
                (messages, maxInputChars) -> {
                    modelCalls.incrementAndGet();
                    return "model";
                },
                (sessionId, messages, maxMessages) -> { },
                mock(ColdMemoryStoreApplicationService.class));

        assertTrue(service.compress(command(messages(4), false, 4000, 4, 2)));
        assertEquals(0, modelCalls.get());
    }

    @Test
    void belowThresholdOrInvalidCommandDoesNotExecuteEffects() {
        AtomicInteger hotCalls = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        ColdMemoryStoreApplicationService coldStore = mock(ColdMemoryStoreApplicationService.class);
        MemoryCompressionApplicationService service = service(
                (messages, maxInputChars) -> {
                    modelCalls.incrementAndGet();
                    return "model";
                },
                (sessionId, messages, maxMessages) -> hotCalls.incrementAndGet(),
                coldStore);

        assertFalse(service.compress(command(messages(3), true, 4000, 4, 2)));
        assertFalse(service.compress(null));
        assertFalse(service.compress(new MemoryCompressionCommand(
                " ", "user-1", messages(4), 4, 2, true, 4000, 20)));

        assertEquals(0, hotCalls.get());
        assertEquals(0, modelCalls.get());
        verify(coldStore, never()).saveItems(anyList());
    }

    @Test
    void missingEffectPortsReturnFalseAfterPlanning() {
        MemoryCompressionApplicationService service = new MemoryCompressionApplicationService(
                new MemoryCompressionPolicy(new MemoryContentHashPolicy()),
                null,
                null,
                null,
                () -> "now");

        assertFalse(service.compress(command(messages(4), false, 4000, 4, 2)));
    }

    @Test
    void trimContextDelegatesToDomainPolicy() {
        MemoryCompressionApplicationService service = service(
                null,
                (sessionId, messages, maxMessages) -> { },
                mock(ColdMemoryStoreApplicationService.class));
        String context = "head-" + "x".repeat(3000) + "-tail";

        String trimmed = service.trimContext(context, 1000);

        assertTrue(trimmed.startsWith("head-"));
        assertTrue(trimmed.endsWith("-tail"));
        assertTrue(trimmed.contains("originalChars=" + context.length()));
    }

    private MemoryCompressionApplicationService service(MemoryModelSummaryPort modelPort,
                                                        HotMemoryReplacePort hotPort,
                                                        ColdMemoryStoreApplicationService coldStore) {
        return new MemoryCompressionApplicationService(
                new MemoryCompressionPolicy(new MemoryContentHashPolicy()),
                modelPort,
                hotPort,
                coldStore,
                () -> "2026-07-21 23:00:00");
    }

    private MemoryCompressionCommand command(List<ColdMemoryMessageSnapshot> messages,
                                             boolean modelEnabled,
                                             int modelMaxInputChars,
                                             int threshold,
                                             int keepRecent) {
        return new MemoryCompressionCommand(
                "session-1",
                "user-1",
                messages,
                threshold,
                keepRecent,
                modelEnabled,
                modelMaxInputChars,
                20);
    }

    private List<ColdMemoryMessageSnapshot> messages(int count) {
        List<ColdMemoryMessageSnapshot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new ColdMemoryMessageSnapshot(
                    "session-1",
                    "user-1",
                    i % 2 == 0 ? "user" : "assistant",
                    "content-" + i,
                    "time-" + i,
                    Map.of()));
        }
        return result;
    }
}
