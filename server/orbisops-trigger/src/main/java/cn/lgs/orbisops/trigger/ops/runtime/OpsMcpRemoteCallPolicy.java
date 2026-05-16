package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Evaluates platform Tool Policy, effect classification, call stage and ChangePackage requirements. */
@Component
public final class OpsMcpRemoteCallPolicy {

    private final OpsMcpToolArgumentPolicyChecker argumentPolicyChecker;

    public OpsMcpRemoteCallPolicy(OpsMcpToolArgumentPolicyChecker argumentPolicyChecker) {
        if (argumentPolicyChecker == null) {
            throw new IllegalArgumentException("MCP_TOOL_ARGUMENT_POLICY_CHECKER_REQUIRED");
        }
        this.argumentPolicyChecker = argumentPolicyChecker;
    }

    public OpsMcpRemoteCallAssessment assess(OpsMcpServerConfig config,
                                             String remoteToolName,
                                             Map<String, Object> schema) {
        Map<String, Object> safe = schema == null ? Map.of() : schema;
        boolean readOnly = bool(safe.get("readOnly"), false);
        String riskLevel = value(safe.get("riskLevel"), "HIGH").toUpperCase();
        String policyStatus = value(
                safe.get("policyStatus"),
                value(safe.get("status"), "MISSING")).toUpperCase();
        String reviewStatus = value(safe.get("reviewStatus"), "UNREVIEWED").toUpperCase();
        String effectType = normalizeEffectType(safe.get("effectType"));
        String effectScope = value(safe.get("effectScope"), "UNKNOWN").toUpperCase();
        String mutability = value(safe.get("mutability"), "UNKNOWN").toUpperCase();
        String capability = capability(config, remoteToolName, readOnly);
        boolean mutating = Set.of("MUTATING", "WRITE", "EXECUTE").contains(capability)
                || mutatingActions(safe.get("allowedActions"));

        return new OpsMcpRemoteCallAssessment(
                value(safe.get("policyId")),
                policyStatus,
                reviewStatus,
                effectType,
                effectScope,
                mutability,
                riskLevel,
                readOnly,
                capability,
                mutating,
                bool(safe.get("requiresApprovedPackage"), true),
                bool(safe.get("requiresHumanApproval"), false),
                bool(safe.get("requiresDryRun"), false),
                bool(safe.get("requiresRollbackPlan"), false),
                bool(safe.get("investigateAllowed"), readOnly),
                bool(safe.get("prepareAllowed"), false),
                bool(safe.get("landAllowed"), false),
                resourceEnvironment(config),
                stage(config),
                value(remoteToolName),
                value(config == null ? null : config.getToolId(),
                        value(config == null ? null : config.getMcpId(),
                                config == null ? "" : config.getName())),
                hasApprovedLandingContextFields(config),
                objectMap(safe.get("argumentPolicy")));
    }

    public void assertAllowed(OpsMcpRemoteCallAssessment assessment, String argumentsJson) {
        if (assessment == null) {
            throw new IllegalArgumentException("MCP_REMOTE_CALL_ASSESSMENT_REQUIRED");
        }
        if (!"ACTIVE".equals(assessment.policyStatus())) {
            throw new SecurityException(policyErrorCode(assessment.policyStatus())
                    + "：MCP 工具策略未处于 ACTIVE，tool=" + assessment.toolName());
        }
        if (!approvedReviewStatus(assessment.reviewStatus())) {
            throw new SecurityException("MCP_POLICY_PENDING_REVIEW：MCP 工具策略未经过审核或系统验证，tool="
                    + assessment.toolName());
        }
        if ("UNKNOWN".equals(assessment.effectType())
                || "UNKNOWN".equals(assessment.effectScope())
                || "UNKNOWN".equals(assessment.mutability())) {
            throw new SecurityException("MCP_TOOL_EFFECT_UNKNOWN：MCP 工具效果未定级，禁止业务调用，tool="
                    + assessment.toolName());
        }
        argumentPolicyChecker.assertAllowed(
                assessment.argumentPolicy(),
                argumentsJson,
                assessment.stage());
        assertStageAllowed(assessment);
    }

    public OpsToolCallStage stage(OpsMcpServerConfig config) {
        if (config != null && Boolean.TRUE.equals(config.getLandingApproved())) {
            return OpsToolCallStage.LANDING;
        }
        return OpsToolCallStage.from(config == null ? null : config.getToolCallStage());
    }

    public boolean requiresChangePackage(String message) {
        return message != null && message.contains("MCP_TOOL_REQUIRES_CHANGE_PACKAGE");
    }

    private void assertStageAllowed(OpsMcpRemoteCallAssessment assessment) {
        OpsToolCallStage stage = assessment.stage();
        boolean highRisk = Set.of("HIGH", "CRITICAL").contains(assessment.riskLevel());
        if (stage == null || OpsToolCallStage.UNKNOWN.equals(stage)) {
            throw new SecurityException("MCP_STAGE_REQUIRED：MCP 工具调用缺少可信执行阶段。");
        }
        if (OpsToolCallStage.SYSTEM_DISCOVERY.equals(stage)) {
            throw new SecurityException("MCP_STAGE_NOT_ALLOWED：SYSTEM_DISCOVERY 阶段不允许实际业务工具调用。");
        }
        if (OpsToolCallStage.INVESTIGATE.equals(stage)) {
            boolean allowed = assessment.readOnly()
                    && assessment.investigateAllowed()
                    && !assessment.requiresApprovedPackage()
                    && Set.of("NO_EFFECT", "READ_EXTERNAL_STATE").contains(assessment.effectType());
            if (!allowed) {
                throw new SecurityException("MCP_STAGE_NOT_ALLOWED：INVESTIGATE 阶段只允许已审核的低风险只读工具。");
            }
            return;
        }
        if (OpsToolCallStage.PREPARE.equals(stage)) {
            if (assessment.productionResource() && !assessment.readOnly()
                    && !safePrepareValidation(assessment)) {
                throw new SecurityException("MCP_STAGE_NOT_ALLOWED：CHAT/PREPARE 只能读取 PROD 或执行已审核的非生产 validation；生产写必须进入已审批 LANDING。");
            }
            return;
        }
        if (OpsToolCallStage.LANDING.equals(stage)) {
            if (!assessment.landingContextFieldsPresent()) {
                throw new SecurityException("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：LANDING 必须绑定已审批 ChangePackage。");
            }
            return;
        }
        if (!assessment.readOnly() || highRisk || assessment.mutating()) {
            if (assessment.approvedLandingContext()) {
                return;
            }
            throw new SecurityException("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：高风险或非只读 MCP 工具必须先生成 ChangePackage，并经过受控验证和审批后由 LandingRuntime 执行。");
        }
    }

    private boolean safePrepareValidation(OpsMcpRemoteCallAssessment assessment) {
        return assessment.platformPolicyApproved()
                && assessment.prepareAllowed()
                && Set.of("VALIDATE_ONLY", "DRY_RUN", "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE")
                .contains(assessment.effectType())
                && !Set.of("PRODUCTION", "TARGET_RESOURCE_WRITE").contains(assessment.effectScope())
                && !Set.of("PROD_MUTATING", "DESTRUCTIVE").contains(assessment.mutability())
                && !assessment.requiresApprovedPackage();
    }

    private boolean approvedReviewStatus(String reviewStatus) {
        String normalized = value(reviewStatus).toUpperCase();
        return "HUMAN_REVIEWED".equals(normalized) || "SYSTEM_VERIFIED".equals(normalized);
    }

    private String capability(OpsMcpServerConfig config,
                              String remoteToolName,
                              boolean readOnly) {
        Map<String, String> capabilities = config == null || config.getToolCapabilities() == null
                ? Map.of()
                : config.getToolCapabilities();
        String capability = value(capabilities.get(value(remoteToolName))).toUpperCase();
        if (!StringUtils.hasText(capability)) {
            capability = value(capabilities.get("*")).toUpperCase();
        }
        return StringUtils.hasText(capability)
                ? capability
                : readOnly ? "READ_ONLY" : "MUTATING";
    }

    private String resourceEnvironment(OpsMcpServerConfig config) {
        Map<String, String> capabilities = config == null || config.getToolCapabilities() == null
                ? Map.of()
                : config.getToolCapabilities();
        return value(capabilities.get("resourceEnvironment"), "unknown").toLowerCase();
    }

    private boolean hasApprovedLandingContextFields(OpsMcpServerConfig config) {
        return config != null
                && Boolean.TRUE.equals(config.getLandingApproved())
                && StringUtils.hasText(value(config.getChangePackageId()))
                && StringUtils.hasText(value(config.getApprovedPackageHash()))
                && config.getApprovedPackageVersion() != null
                && config.getApprovedPackageVersion() > 0;
    }

    private String policyErrorCode(String policyStatus) {
        return switch (value(policyStatus).toUpperCase()) {
            case "PENDING_REVIEW" -> "MCP_POLICY_PENDING_REVIEW";
            case "STALE" -> "MCP_POLICY_STALE";
            case "DISABLED", "REJECTED" -> "MCP_POLICY_DISABLED";
            default -> "MCP_POLICY_MISSING";
        };
    }

    private boolean mutatingActions(Object allowedActions) {
        if (allowedActions instanceof List<?> list) {
            return list.stream().map(String::valueOf).anyMatch(this::mutatingAction);
        }
        if (allowedActions instanceof String text) {
            return Arrays.stream(text.split("[,;\\s]+")).anyMatch(this::mutatingAction);
        }
        return true;
    }

    private boolean mutatingAction(String action) {
        String normalized = value(action).toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        return normalized.contains("WRITE")
                || normalized.contains("UPDATE")
                || normalized.contains("DELETE")
                || normalized.contains("INSERT")
                || normalized.contains("EXECUTE")
                || normalized.contains("RESTART")
                || normalized.contains("CONFIG")
                || normalized.contains("MUTATE");
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String text && StringUtils.hasText(text) && text.trim().startsWith("{")) {
            try {
                return JSON.parseObject(text, LinkedHashMap.class);
            } catch (Exception ignored) {
            }
        }
        return Map.of();
    }

    private String normalizeEffectType(Object value) {
        String effectType = value(value, "UNKNOWN").toUpperCase();
        return "MUTATE_TEMP_RESOURCE".equals(effectType) ? "MUTATE_EPHEMERAL" : effectType;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            String text = String.valueOf(value);
            return "true".equalsIgnoreCase(text)
                    || "1".equals(text)
                    || "yes".equalsIgnoreCase(text);
        }
        return fallback;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(Object value, String fallback) {
        String text = value(value);
        return StringUtils.hasText(text) ? text : value(fallback);
    }
}
