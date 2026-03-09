package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryQueryCommand;
import cn.lgs.orbisops.application.memory.MemoryQueryResult;
import cn.lgs.orbisops.application.memory.MemorySelectionReference;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsMemoryQueryMapperTest {

    private final OpsMemoryQueryMapper mapper = new OpsMemoryQueryMapper();

    @Test
    void mapsTriggerMetadataAndRuntimeConfigurationToTypedCommand() {
        MemoryQueryCommand command = mapper.command(
                "s1",
                "u1",
                "query",
                Map.of(
                        "scene", " CUSTOM_SCENE ",
                        "taskType", " TASK ",
                        "projectId", " demo-project "),
                3,
                4,
                5,
                3600,
                900,
                true,
                6);

        assertEquals("s1", command.sessionId());
        assertEquals("CUSTOM_SCENE", command.explicitScene());
        assertEquals("TASK", command.taskType());
        assertEquals("demo-project", command.projectId());
        assertEquals(3, command.itemMatchLimit());
        assertEquals(4, command.hotMessageLimit());
        assertEquals(5, command.semanticTopK());
        assertEquals(3600, command.contextMaxChars());
        assertEquals(900L, command.timeoutMillis());
    }

    @Test
    void mapsTypedResultToCompatibilitySelection() {
        MemorySelectionReference reference = new MemorySelectionReference(
                "ctx-1", 1, "hash", "TYPE", "SCOPE", "scope-id",
                "source", "hash", "2026-07-21T11:00:00Z", false);

        OpsMemorySelection selection = mapper.selection(new MemoryQueryResult(
                "rendered context",
                List.of(reference)));

        assertEquals("rendered context", selection.context());
        assertEquals(1, selection.refs().size());
        assertEquals("ctx-1", selection.refs().get(0).get("memoryId"));
        assertEquals(false, selection.refs().get(0).get("verified"));
    }

    @Test
    void handlesNullMetadataAndResult() {
        MemoryQueryCommand command = mapper.command(
                "s1", "u1", "query", null, 0, 0, 0, 0, 0, false, 0);
        OpsMemorySelection selection = mapper.selection(null);

        assertEquals("", command.explicitScene());
        assertEquals("", command.taskType());
        assertEquals("", command.projectId());
        assertEquals(1, command.itemMatchLimit());
        assertEquals(1200, command.contextMaxChars());
        assertEquals("", selection.context());
        assertEquals(List.of(), selection.refs());
    }
}
