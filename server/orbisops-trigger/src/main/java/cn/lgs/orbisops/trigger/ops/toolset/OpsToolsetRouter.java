package cn.lgs.orbisops.trigger.ops.toolset;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OpsToolsetRouter {

    public static final String LANDING_INTERNAL_CALLER = "LANDING_RUNTIME";
    /** Process-local nonce used only as an in-JVM trusted-call marker; never a persisted/client credential. */
    public static final String LANDING_RUNTIME_TOKEN = UUID.randomUUID().toString();

    public Map<String, Object> decide(OpsToolsetDefinition toolset,
                                      OpsToolDefinition tool,
                                      boolean landingRuntime,
                                      Map<String, Object> landingContext) {
        return decide(toolset, tool,
                landingRuntime ? OpsToolExecutionScope.APPROVED_LANDING : OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                landingContext);
    }

    public Map<String, Object> decide(OpsToolsetDefinition toolset,
                                      OpsToolDefinition tool,
                                      OpsToolExecutionScope executionScope,
                                      Map<String, Object> landingContext) {
        OpsToolExecutionScope scope = executionScope == null
                ? OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW : executionScope;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolsetId", toolset == null ? "" : toolset.getToolsetId());
        result.put("toolName", tool == null ? "" : tool.getToolName());
        result.put("executionScope", scope.name());
        result.put("readOnly", tool != null && tool.isReadOnly());
        result.put("writesRepairWorkspace", tool != null && tool.isWritesRepairWorkspace());
        result.put("writesTargetResource", tool != null && tool.isWritesTargetResource());
        result.put("requiresChangePackage", tool != null && tool.isRequiresChangePackage());
        result.put("requiresApproval", tool != null && tool.isRequiresApproval());
        if (tool == null || toolset == null || !toolset.isEnabled() || !tool.isEnabled()) {
            result.put("allowed", false);
            result.put("decision", "TOOL_NOT_AVAILABLE");
            result.put("reasonCode", "TOOL_NOT_AVAILABLE");
            return result;
        }
        if (OpsToolExecutionScope.SYSTEM_DISCOVERY.equals(scope)) {
            if (tool.isWritesTargetResource() || tool.isWritesRepairWorkspace()
                    || tool.isRequiresChangePackage() || tool.isRequiresApproval()) {
                return blocked(result, "SYSTEM_DISCOVERY_BLOCKED",
                        "SYSTEM_DISCOVERY 只允许工具目录、schema 和策略发现，不允许业务工具执行。");
            }
            return allowed(result);
        }
        if (OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.equals(scope)) {
            if (tool.isWritesTargetResource()) {
                return blocked(result, "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE",
                        "该工具会修改目标资源，不能在审核前 Agentic Workflow 中真实执行。请先生成 ChangePackage、完成验证和人工审批。");
            }
            return allowed(result);
        }
        if (!approvedLandingContext(landingContext)) {
            return blocked(result, "APPROVED_LANDING_CONTEXT_REQUIRED",
                    "APPROVED_LANDING 必须绑定 landingApproved、changePackageId、approvedVersion 和 approvedPackageHash。");
        }
        if (!LANDING_INTERNAL_CALLER.equals(value(landingContext.get("internalCaller")))
                || !LANDING_RUNTIME_TOKEN.equals(value(landingContext.get("landingRuntimeToken")))) {
            return blocked(result, "APPROVED_LANDING_INTERNAL_CALLER_REQUIRED",
                    "APPROVED_LANDING 只能由平台固定 LandingRuntime 内部发起，普通 Agent/ToolCallback 不能伪造。");
        }
        // Approval authorizes entry into Landing. ChangePackage is the frozen task/audit
        // baseline, not a second per-tool/per-argument RBAC layer.
        return allowed(result);
    }

    private Map<String, Object> blocked(Map<String, Object> result, String reasonCode, String message) {
        result.put("allowed", false);
        result.put("decision", reasonCode);
        result.put("reasonCode", reasonCode);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> allowed(Map<String, Object> result) {
        result.put("allowed", true);
        result.put("decision", "ALLOWED");
        result.put("reasonCode", "ALLOWED");
        return result;
    }

    private boolean approvedLandingContext(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        return Boolean.TRUE.equals(context.get("landingApproved"))
                && StringUtils.hasText(value(context.get("changePackageId")))
                && StringUtils.hasText(value(context.get("approvedPackageHash")))
                && intValue(context.get("approvedPackageVersion")) > 0;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
