package cn.lgs.orbisops.trigger.application.memory;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsSemanticRetrievalFailureAdapterTest {

    @Test
    void firstDegradationMarksObserverAsLoggedAcrossAllOperations() {
        OpsSemanticRetrievalFailureAdapter adapter = new OpsSemanticRetrievalFailureAdapter();

        assertEquals(false, ReflectionTestUtils.getField(adapter, "unavailableLogged"));
        adapter.onFailure("vector-recall", new IllegalStateException("vector down"));
        assertEquals(true, ReflectionTestUtils.getField(adapter, "unavailableLogged"));
        adapter.onFailure("lexical-recall", new IllegalStateException("fts down"));
        adapter.onFailure("fusion", new IllegalStateException("fusion down"));
        adapter.onWriteFailure("vector-write", new IllegalStateException("vector write down"));
        adapter.onWriteFailure("lexical-write", new IllegalStateException("lexical write down"));
        adapter.onClearFailure(new IllegalStateException("clear down"));
        assertEquals(true, ReflectionTestUtils.getField(adapter, "unavailableLogged"));
    }

    @Test
    void directDegradationUsesSameOneTimeState() {
        OpsSemanticRetrievalFailureAdapter adapter = new OpsSemanticRetrievalFailureAdapter();

        adapter.onDegraded("write unavailable");

        assertEquals(true, ReflectionTestUtils.getField(adapter, "unavailableLogged"));
    }
}
