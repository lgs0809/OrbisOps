package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.OpsBuiltinSubAgentRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsBuiltinSubAgentRoleTest {

    @Test
    void legacyRoleAliasesMustMapToCurrentRoleBoundaries() {
        assertEquals(OpsBuiltinSubAgentRole.MAIN_ASSISTANT, OpsBuiltinSubAgentRole.parse("general"));
        assertEquals(OpsBuiltinSubAgentRole.EVIDENCE_EXPLORER, OpsBuiltinSubAgentRole.parse("data-agent"));
        assertEquals(OpsBuiltinSubAgentRole.EVIDENCE_EXPLORER, OpsBuiltinSubAgentRole.parse("investigator"));
    }

    @Test
    void mainAssistantCanUseTheCompletePreApprovalToolSurface() {
        assertEquals(
                true,
                OpsBuiltinSubAgentRole.MAIN_ASSISTANT.defaultAllowedTools()
                        .contains("PrepareChangePackage"));
        assertEquals(
                true,
                OpsBuiltinSubAgentRole.MAIN_ASSISTANT.defaultAllowedTools()
                        .contains("code_edit"));
        assertEquals(
                false,
                OpsBuiltinSubAgentRole.MAIN_ASSISTANT.defaultAllowedTools()
                        .contains("landing"));
    }

    @Test
    void unknownRoleStillFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> OpsBuiltinSubAgentRole.parse("production-writer"));
    }
}
