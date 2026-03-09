package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.HotMemoryQueryPort;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextCompressor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextMemoryService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryExtractor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryItem;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSemanticMemoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMemoryPostProcessingAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsTypedPortsToLegacyRuntimeComponents() {
        OpsSemanticMemoryStore semanticStore = mock(OpsSemanticMemoryStore.class);
        OpsMemoryExtractor extractor = mock(OpsMemoryExtractor.class);
        ObjectProvider<OpsContextMemoryService> contextProvider = mock(ObjectProvider.class);
        OpsContextMemoryService contextMemoryService = mock(OpsContextMemoryService.class);
        HotMemoryQueryPort hotMemoryQueryPort = mock(HotMemoryQueryPort.class);
        OpsContextCompressor compressor = mock(OpsContextCompressor.class);
        when(contextProvider.getIfAvailable()).thenReturn(contextMemoryService);
        OpsMemoryItem extractedItem = OpsMemoryItem.builder()
                .sessionId("s1")
                .userId("u1")
                .memoryType("PROJECT_CONTEXT")
                .content("DDD migration")
                .importance(BigDecimal.valueOf(0.9))
                .metadata(Map.of("turn_index", 7))
                .build();
        when(extractor.extract(any(OpsMemoryMessage.class))).thenReturn(List.of(extractedItem));
        List<MemoryMessageView> recentViews = List.of(new MemoryMessageView(
                "s1",
                "u1",
                "user",
                "recent",
                "",
                Map.of()));
        when(hotMemoryQueryPort.recent("s1", 8)).thenReturn(recentViews);
        List<OpsMemoryMessage> recentMessages = List.of(OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content("recent")
                .createdAt("")
                .metadata(Map.of())
                .build());
        OpsMemoryPostProcessingAdapter adapter = new OpsMemoryPostProcessingAdapter(
                semanticStore,
                extractor,
                contextProvider,
                hotMemoryQueryPort,
                compressor);
        MemoryMessageView message = new MemoryMessageView(
                "s1",
                "u1",
                "user",
                "captured",
                "2026-07-21 18:00:00",
                Map.of("turn_index", 7));

        adapter.append(message);
        List<ColdMemoryItemSnapshot> extracted = adapter.extract(message);
        adapter.saveExtractedItems(extracted);
        adapter.compress("s1", "u1", 8);

        ArgumentCaptor<OpsMemoryMessage> messageCaptor = ArgumentCaptor.forClass(OpsMemoryMessage.class);
        verify(semanticStore).appendMessage(messageCaptor.capture());
        assertEquals("captured", messageCaptor.getValue().getContent());
        assertEquals("PROJECT_CONTEXT", extracted.get(0).memoryType());
        ArgumentCaptor<List<ColdMemoryItemSnapshot>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(contextMemoryService).saveExtractedItems(itemsCaptor.capture());
        assertEquals("DDD migration", itemsCaptor.getValue().get(0).content());
        verify(compressor).compressIfNeeded(
                eq("s1"),
                eq("u1"),
                eq(recentMessages),
                eq(8));
    }
}
