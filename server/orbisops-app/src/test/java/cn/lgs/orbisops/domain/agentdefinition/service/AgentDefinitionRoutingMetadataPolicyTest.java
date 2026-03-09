package cn.lgs.orbisops.domain.agentdefinition.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDefinitionRoutingMetadataPolicyTest {

    private final AgentDefinitionRoutingMetadataPolicy policy =
            new AgentDefinitionRoutingMetadataPolicy();

    @Test
    void manualWorkflowDoesNotRequireAutomaticRoutingMetadata() {
        assertDoesNotThrow(() -> policy.validate(
                "SPECIALIZED_WORKFLOW",
                "MANUAL_ONLY",
                false,
                List.of(),
                List.of(),
                List.of()));
    }

    @Test
    void automaticWorkflowSelectionIsRejectedEvenWithCompleteMetadata() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        "SPECIALIZED_WORKFLOW",
                        "MANUAL_ONLY",
                        true,
                        List.of("订单失败排查"),
                        List.of("只解释概念"),
                        List.of("订单失败")));
        assertEquals("WORKFLOW_REQUIRES_EXPLICIT_USER_SELECTION", error.getMessage());
    }

    @Test
    void removedAutoEligibleModeFailsClosed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        "SPECIALIZED_WORKFLOW",
                        "AUTO_ELIGIBLE",
                        false,
                        List.of("订单失败排查"),
                        List.of("只解释概念"),
                        List.of("订单失败")));
        assertEquals("不支持的 Workflow 调用模式：AUTO_ELIGIBLE", error.getMessage());
    }
}
