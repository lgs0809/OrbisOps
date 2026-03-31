package cn.lgs.orbisops.domain.toolset.model.business;

import cn.lgs.orbisops.domain.toolset.model.ApprovalRequirement;
import cn.lgs.orbisops.domain.toolset.model.CompensationCapability;
import cn.lgs.orbisops.domain.toolset.model.IdempotencyCapability;
import cn.lgs.orbisops.domain.toolset.model.ReconciliationCapability;
import cn.lgs.orbisops.domain.toolset.model.ToolEffect;
import cn.lgs.orbisops.domain.toolset.model.ToolGovernance;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolTrustLevel;

/** Platform-authoritative contract; MCP discovery metadata cannot override these facts. */
public final class UpdateAlertThresholdPolicy {

    public static final String TOOLSET_ID = "ops.alert-threshold.write";
    public static final String TOOL_NAME = "update_alert_threshold";
    public static final String STATE_QUERY_TOOL_NAME = "get_alert_threshold";
    public static final String RECEIPT_QUERY_TOOL_NAME = "get_operation_receipt";
    public static final String COMPENSATION_TOOL_NAME = "restore_alert_threshold";

    public static final String INPUT_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["projectId","metric","expectedValue","expectedVersion","newValue","approvalId","executionKey","deadline","actor"],
              "properties":{
                "projectId":{"type":"string","minLength":1,"maxLength":128},
                "metric":{"type":"string","pattern":"^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"},
                "expectedValue":{"type":"number","minimum":-1000000000000,"maximum":1000000000000},
                "expectedVersion":{"type":"integer","minimum":0},
                "newValue":{"type":"number","minimum":-1000000000000,"maximum":1000000000000},
                "approvalId":{"type":"string","minLength":1,"maxLength":128},
                "executionKey":{"type":"string","minLength":1,"maxLength":256},
                "deadline":{"type":"string","format":"date-time"},
                "actor":{"type":"string","minLength":1,"maxLength":128}
              }
            }
            """;

    public static final String OUTPUT_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":true,
              "required":["status","receiptId","operation","executionKey","projectId","metric","approvalId","actor","expectedVersion","hashVersion","operationInputHash","previousValue","currentValue","currentVersion","resultHash","completedAt"],
              "properties":{
                "status":{"const":"SUCCEEDED"},
                "receiptId":{"type":"string","minLength":1},
                "operation":{"type":"string","enum":["update_alert_threshold","restore_alert_threshold"]},
                "executionKey":{"type":"string","minLength":1,"maxLength":256},
                "projectId":{"type":"string","minLength":1,"maxLength":128},
                "metric":{"type":"string","pattern":"^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"},
                "approvalId":{"type":"string","minLength":1,"maxLength":128},
                "actor":{"type":"string","minLength":1,"maxLength":128},
                "expectedVersion":{"type":"integer","minimum":0},
                "hashVersion":{"const":1},
                "operationInputHash":{"type":"string","pattern":"^[a-fA-F0-9]{64}$"},
                "previousValue":{"type":"number"},
                "currentValue":{"type":"number"},
                "currentVersion":{"type":"integer","minimum":1},
                "resultHash":{"type":"string","pattern":"^sha256:[a-fA-F0-9]{64}$"},
                "completedAt":{"type":"string","format":"date-time"}
              }
            }
            """;

    private UpdateAlertThresholdPolicy() {
    }

    public static ToolGovernance governance() {
        return new ToolGovernance(
                ToolEffect.SIDE_EFFECTING,
                ToolRiskLevel.HIGH,
                ApprovalRequirement.OPERATOR_APPROVAL,
                IdempotencyCapability.SERVER_RECEIPT,
                ReconciliationCapability.QUERY_BY_EXECUTION_KEY,
                CompensationCapability.STATE_RESTORE,
                ToolTrustLevel.PLATFORM_CONFIGURED);
    }

    public static boolean matches(String toolsetId, String toolName) {
        return TOOLSET_ID.equals(text(toolsetId)) && TOOL_NAME.equals(text(toolName));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
