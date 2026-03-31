package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsToolExecutionPolicyTest {

    private final OpsToolExecutionPolicy policy = new OpsToolExecutionPolicy(ToolExposureSettings.defaults());

    @Test
    void explicitKnownCapabilityIsAcceptedAndMissingCapabilityFailsClosed() {
        assertTrue(policy.allowTool(tool("restartPod", "重启 Kubernetes Pod"), "mutating"));
        assertFalse(policy.allowTool(tool("queryPrometheus", "查询 Prometheus 指标"), null));
        assertFalse(policy.allowTool(null, "read_only"));
    }

    @Test
    void capabilityMetadataNotPresentationTextControlsExposure() {
        assertTrue(policy.allowTool(
                tool("executeSql", "执行 SQL 变更"), "read_only"));
        assertTrue(policy.allowTool(
                tool("restartPod", "重启 Kubernetes Pod"), "notification"));
        assertTrue(policy.allowTool(
                tool("restartPod", "只是一个看起来危险的名字"), "execute"));
        assertFalse(policy.allowTool(
                tool("queryPrometheus", "查询 Prometheus 指标"), "unknown-safe"));
    }

    private ToolCallback tool(String name, String description) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(definition.description()).thenReturn(description);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }
}
