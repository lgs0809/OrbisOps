package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MemorySessionClearApplicationServiceTest {

    @Test
    void clearsHotColdSemanticAndCaptureStateInOrder() {
        List<String> operations = new ArrayList<>();
        MemoryCaptureApplicationService captureService = mock(MemoryCaptureApplicationService.class);
        doAnswer(invocation -> {
            operations.add("capture:" + invocation.getArgument(0));
            return null;
        }).when(captureService).clearSessionState("s1");
        MemorySessionClearApplicationService service = new MemorySessionClearApplicationService(
                sessionId -> operations.add("hot:" + sessionId),
                coldStore(operations),
                sessionId -> operations.add("semantic:" + sessionId),
                captureService,
                null);

        MemorySessionClearResult result = service.clear(" s1 ");

        assertTrue(result.attempted());
        assertTrue(result.fullyCleared());
        assertEquals(List.of(), result.failedOperations());
        assertEquals(List.of(
                "hot:s1",
                "cold:s1",
                "semantic:s1",
                "capture:s1"), operations);
    }

    @Test
    void isolatesFailuresAndContinuesRemainingClearOperations() {
        List<String> operations = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        MemoryCaptureApplicationService captureService = mock(MemoryCaptureApplicationService.class);
        doAnswer(invocation -> {
            operations.add("capture");
            return null;
        }).when(captureService).clearSessionState("s1");
        MemorySessionClearApplicationService service = new MemorySessionClearApplicationService(
                sessionId -> {
                    operations.add("hot");
                    throw new IllegalStateException("hot down");
                },
                coldStore(operations),
                sessionId -> {
                    operations.add("semantic");
                    throw new IllegalStateException("semantic down");
                },
                captureService,
                (operation, error) -> observed.add(operation + ":" + error.getMessage()));

        MemorySessionClearResult result = service.clear("s1");

        assertTrue(result.attempted());
        assertFalse(result.fullyCleared());
        assertEquals(List.of("hot-clear", "semantic-clear"), result.failedOperations());
        assertEquals(List.of("hot", "cold:s1", "semantic", "capture"), operations);
        assertEquals(List.of(
                "hot-clear:hot down",
                "semantic-clear:semantic down"), observed);
    }

    @Test
    void unavailablePortIsReportedWithoutBlockingAvailablePorts() {
        List<String> operations = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        MemoryCaptureApplicationService captureService = mock(MemoryCaptureApplicationService.class);
        doAnswer(invocation -> {
            operations.add("capture");
            return null;
        }).when(captureService).clearSessionState("s1");
        MemorySessionClearApplicationService service = new MemorySessionClearApplicationService(
                null,
                coldStore(operations),
                sessionId -> operations.add("semantic"),
                captureService,
                (operation, error) -> observed.add(operation));

        MemorySessionClearResult result = service.clear("s1");

        assertEquals(List.of("hot-clear"), result.failedOperations());
        assertEquals(List.of("cold:s1", "semantic", "capture"), operations);
        assertEquals(List.of("hot-clear"), observed);
    }

    @Test
    void blankSessionIsSkippedWithoutCallingPorts() {
        MemoryCaptureApplicationService captureService = mock(MemoryCaptureApplicationService.class);
        List<String> operations = new ArrayList<>();
        MemorySessionClearApplicationService service = new MemorySessionClearApplicationService(
                sessionId -> operations.add("hot"),
                coldStore(operations),
                sessionId -> operations.add("semantic"),
                captureService,
                null);

        MemorySessionClearResult result = service.clear(" ");

        assertFalse(result.attempted());
        assertFalse(result.fullyCleared());
        assertTrue(operations.isEmpty());
        verifyNoInteractions(captureService);
    }

    private ColdMemoryStoreApplicationService coldStore(List<String> operations) {
        return new ColdMemoryStoreApplicationService(new IColdMemoryRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public void appendMessage(ColdMemoryMessageSnapshot message) {
            }

            @Override
            public void saveItems(List<ColdMemoryItemSnapshot> items) {
            }

            @Override
            public List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit) {
                return List.of();
            }

            @Override
            public void clear(String sessionId) {
                operations.add("cold:" + sessionId);
            }
        }, () -> true, null);
    }
}
