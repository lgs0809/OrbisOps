package cn.lgs.orbisops.application.agent;

import java.util.List;
import java.util.Map;

public record OpsFailureDescriptor(
        String reasonCode,
        String stage,
        OpsFailureCategory category,
        String safeUserMessage,
        boolean retryable,
        OpsSideEffectState sideEffectState,
        List<String> missingRequirements,
        List<String> allowedRecoveryActions,
        Map<String, Object> references) {

    public OpsFailureDescriptor {
        reasonCode = text(reasonCode, "MAIN_AGENT_ACTION_FAILED");
        stage = text(stage, "MAIN_AGENT");
        category = category == null ? OpsFailureCategory.UNKNOWN : category;
        safeUserMessage = text(safeUserMessage, "任务未完成，系统已记录失败原因。");
        sideEffectState = sideEffectState == null ? OpsSideEffectState.UNKNOWN : sideEffectState;
        missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
        allowedRecoveryActions = allowedRecoveryActions == null ? List.of() : List.copyOf(allowedRecoveryActions);
        references = references == null ? Map.of() : Map.copyOf(references);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
