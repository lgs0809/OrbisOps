package cn.lgs.orbisops.application.audit;

public record ConfigAuditCommand(
        String projectId,
        String agentId,
        String moduleName,
        String actionName,
        String targetType,
        String targetId,
        String riskLevel,
        String resultStatus,
        String operatorId,
        String operatorName,
        String operatorRole,
        String clientIp,
        String traceId,
        String beforeJson,
        String afterJson) {

    public ConfigAuditCommand {
        projectId = value(projectId);
        agentId = value(agentId);
        moduleName = value(moduleName);
        actionName = value(actionName);
        targetType = value(targetType);
        targetId = value(targetId);
        riskLevel = value(riskLevel);
        resultStatus = value(resultStatus);
        operatorId = value(operatorId);
        operatorName = value(operatorName);
        operatorRole = value(operatorRole);
        clientIp = value(clientIp);
        traceId = value(traceId);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
