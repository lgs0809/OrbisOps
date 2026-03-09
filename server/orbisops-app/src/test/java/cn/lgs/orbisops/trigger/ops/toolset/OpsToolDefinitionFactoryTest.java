package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolDefinitionFactoryTest {

    private final OpsToolDefinitionFactory factory = new OpsToolDefinitionFactory();

    @Test
    void readToolMustRemainLowRiskAndSideEffectFree() {
        OpsToolDefinition tool = factory.read("read_tool", "read description");

        assertEquals("read_tool", tool.getToolName());
        assertEquals("MCP", tool.getAdapterType());
        assertTrue(tool.isReadOnly());
        assertFalse(tool.isWritesRepairWorkspace());
        assertFalse(tool.isWritesTargetResource());
        assertFalse(tool.isRequiresChangePackage());
        assertFalse(tool.isRequiresApproval());
        assertEquals("LOW", tool.getRiskLevel());
        assertTrue(tool.isEnabled());
    }

    @Test
    void validationRepairAndWorkflowToolsMustPreserveDistinctSemantics() {
        OpsToolDefinition validation = factory.validation("validate", "validation");
        OpsToolDefinition repair = factory.repair("code_edit");
        OpsToolDefinition inspection = factory.inspection("inspection_create", "create");
        OpsToolDefinition alert = factory.alertTrigger("alert_create", "create");
        OpsToolDefinition channel = factory.channel("channel_send", "send");

        assertEquals("LOCAL_VALIDATION", validation.getAdapterType());
        assertFalse(validation.isReadOnly());
        assertEquals("MEDIUM", validation.getRiskLevel());
        assertEquals("CODE_REPAIR", repair.getAdapterType());
        assertTrue(repair.isWritesRepairWorkspace());
        assertEquals("MEDIUM", repair.getRiskLevel());
        assertEquals("INSPECTION_TASK", inspection.getAdapterType());
        assertEquals("ALERT_TRIGGER", alert.getAdapterType());
        assertEquals("CHANNEL", channel.getAdapterType());
        assertEquals("LOW", inspection.getRiskLevel());
        assertEquals("LOW", alert.getRiskLevel());
        assertEquals("LOW", channel.getRiskLevel());
    }

    @Test
    void targetWriteToolsMustRequireApprovedChangePackage() {
        OpsToolDefinition remote = factory.targetWrite("nacos_publish", "Nacos 发布");

        assertEquals("MCP", remote.getAdapterType());
        assertEquals("Nacos 发布", remote.getDisplayName());
        assertFalse(remote.isReadOnly());
        assertFalse(remote.isWritesRepairWorkspace());
        assertTrue(remote.isWritesTargetResource());
        assertTrue(remote.isRequiresChangePackage());
        assertTrue(remote.isRequiresApproval());
        assertEquals("HIGH", remote.getRiskLevel());
        assertTrue(remote.isEnabled());
    }
}
