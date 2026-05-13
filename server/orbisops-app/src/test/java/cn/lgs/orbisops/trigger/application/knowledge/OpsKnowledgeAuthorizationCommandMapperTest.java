package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeGlobalAuthorizationCommand;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsKnowledgeAuthorizationCommandMapperTest {

    private final OpsKnowledgeAuthorizationCommandMapper mapper =
            new OpsKnowledgeAuthorizationCommandMapper();

    @Test
    void mapsKnowledgeAliasesAndIgnoresForgedOperator() {
        KnowledgeGlobalAuthorizationCommand command = mapper.enableGlobal(
                "project-1",
                Map.of("knowledgeTag", "global-kb", "status", "disabled", "enabledBy", "mallory"),
                "alice");

        assertEquals("project-1", command.projectId());
        assertEquals("global-kb", command.globalKbId());
        assertEquals(KnowledgeStatus.DISABLED, command.status());
        assertEquals("alice", command.enabledBy());
    }

    @Test
    void defaultsStatusToEnabled() {
        KnowledgeGlobalAuthorizationCommand command = mapper.enableGlobal(
                "project-1", Map.of("kbId", "global-kb"), "alice");

        assertEquals(KnowledgeStatus.ENABLED, command.status());
    }

    @Test
    void unknownAuthorizationStatusFailsClosedAtTriggerBoundary() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> mapper.enableGlobal("project-1",
                        Map.of("globalKbId", "global-kb", "status", "pending"), "alice"));

        assertEquals("KNOWLEDGE_STATUS_UNKNOWN:PENDING", failure.getMessage());
    }
}
