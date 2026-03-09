package cn.lgs.orbisops.application.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryClearApplicationServiceTest {

    @Test
    void trimsSessionAndReturnsPersistenceResult() {
        AtomicReference<String> session = new AtomicReference<>();
        SemanticMemoryClearApplicationService service = new SemanticMemoryClearApplicationService(
                value -> {
                    session.set(value);
                    return true;
                },
                null);

        boolean cleared = service.clear(" session-1 ");

        assertTrue(cleared);
        assertEquals("session-1", session.get());
    }

    @Test
    void falsePersistenceResultIsPreserved() {
        SemanticMemoryClearApplicationService service = new SemanticMemoryClearApplicationService(
                sessionId -> false,
                null);

        assertFalse(service.clear("session-1"));
    }

    @Test
    void invalidSessionOrMissingPersistenceShortCircuits() {
        AtomicInteger calls = new AtomicInteger();
        SemanticMemoryClearApplicationService service = new SemanticMemoryClearApplicationService(
                sessionId -> {
                    calls.incrementAndGet();
                    return true;
                },
                null);
        SemanticMemoryClearApplicationService missing = new SemanticMemoryClearApplicationService(
                null,
                null);

        assertFalse(service.clear(null));
        assertFalse(service.clear(" "));
        assertFalse(missing.clear("session-1"));
        assertEquals(0, calls.get());
    }

    @Test
    void persistenceFailureIsObservedAndReturnsFalse() {
        List<String> failures = new ArrayList<>();
        SemanticMemoryClearApplicationService service = new SemanticMemoryClearApplicationService(
                sessionId -> {
                    throw new IllegalStateException("storage down");
                },
                error -> failures.add(error.getMessage()));

        boolean cleared = service.clear("session-1");

        assertFalse(cleared);
        assertEquals(List.of("storage down"), failures);
    }

    @Test
    void observerFailureIsContained() {
        SemanticMemoryClearApplicationService service = new SemanticMemoryClearApplicationService(
                sessionId -> {
                    throw new IllegalStateException("storage down");
                },
                error -> {
                    throw new IllegalStateException("observer down");
                });

        assertFalse(service.clear("session-1"));
    }
}
