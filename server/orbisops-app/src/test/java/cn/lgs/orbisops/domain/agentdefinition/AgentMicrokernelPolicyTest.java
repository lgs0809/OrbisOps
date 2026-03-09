package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentMicrokernelPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMicrokernelPolicyTest {

    private final AgentMicrokernelPolicy policy = new AgentMicrokernelPolicy();

    @Test
    void mainAssistantSupportsPlatformMicrokernel() {
        assertTrue(policy.supportsBuiltInMicrokernel(List.of("main assistant")));
    }

    @Test
    void legacyRoleCollectionDoesNotMasqueradeAsMainAssistant() {
        assertFalse(policy.supportsBuiltInMicrokernel(List.of(
                "EVIDENCE_EXPLORER",
                "CODE_INVESTIGATOR",
                "REPAIR_WORKER",
                "VALIDATION_RUNNER",
                "EVIDENCE_CRITIC",
                "CHANGE_PACKAGE_COMPOSER")));
    }
}
