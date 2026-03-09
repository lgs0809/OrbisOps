package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import cn.lgs.orbisops.domain.runtime.tool.service.ToolExposurePolicy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Spring AI facade for governed model-tool exposure. */
@Service
public class OpsToolExecutionPolicy {

    private final ToolExposureSettings settings;
    private final ToolExposurePolicy exposurePolicy = new ToolExposurePolicy();
    private final OpsToolOperationBoundaryProjector boundaryProjector = new OpsToolOperationBoundaryProjector();

    public OpsToolExecutionPolicy(ToolExposureSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("TOOL_EXPOSURE_SETTINGS_REQUIRED");
        }
        this.settings = settings;
    }

    public boolean allowTool(ToolCallback callback, String declaredCapability) {
        if (callback == null) return false;
        return exposurePolicy.allows(declaredCapability, settings);
    }

    public String blockedReason(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        String name = definition == null ? "" : definition.name();
        return "运维 AI 当前 Tool Exposure Policy 拒绝 MCP 工具 " + name
                + "：必须显式声明受支持的 read/write/notification capability，具体权限由 Runtime Stage 与资源环境决定。";
    }

    public Map<String, Object> operationBoundary() {
        return boundaryProjector.project(settings);
    }
}
