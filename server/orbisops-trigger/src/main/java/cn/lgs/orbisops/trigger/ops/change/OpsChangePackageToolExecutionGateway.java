package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Routes PREPARE creation through the unified ToolsetRouter execution boundary. */
final class OpsChangePackageToolExecutionGateway {

    private final OpsToolExecutionService toolExecutionService;
    private final OpsOwningPrepareValidationService owningPrepareValidationService;

    OpsChangePackageToolExecutionGateway(OpsToolExecutionService toolExecutionService) {
        this(toolExecutionService, null);
    }

    OpsChangePackageToolExecutionGateway(OpsToolExecutionService toolExecutionService,
                                         OpsOwningPrepareValidationService owningPrepareValidationService) {
        this.toolExecutionService = toolExecutionService;
        this.owningPrepareValidationService = owningPrepareValidationService;
    }

    Map<String, Object> execute(Map<String, Object> request, Context context) {
        if (toolExecutionService == null) {
            throw new SecurityException(
                    "OpsToolExecutionService 未初始化，ChangePackage 工具不能绕过统一 ToolsetRouter。");
        }
        if (context == null) throw new IllegalArgumentException("CHANGE_PACKAGE_EXECUTION_CONTEXT_REQUIRED");
        Map<String, Object> arguments = request == null ? Map.of() : request;
        if (owningPrepareValidationService != null) {
            arguments = owningPrepareValidationService.enrich(arguments, context.actor());
        }
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("projectId", context.projectId());
        execution.put("userId", context.actor());
        execution.put("runId", context.runId());
        execution.put("executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name());
        execution.put("toolsetId", "change_package");
        execution.put("toolName", "change_package_create");
        execution.put("arguments", arguments);
        execution.put("idempotencyKey", "change-package:prepare:" + CanonicalObjectHasher.sha256(Map.of(
                "projectId", context.projectId(),
                "runId", context.runId(),
                "arguments", arguments)));
        if (context.runtimeRequestPresent()) {
            execution.put("sessionId", context.sessionId());
            execution.put("metadata", context.metadata());
        }
        return toolExecutionService.execute(execution, context.actor());
    }

    record Context(String projectId,
                   String actor,
                   String runId,
                   String sessionId,
                   Map<String, Object> metadata,
                   boolean runtimeRequestPresent) {
        Context {
            projectId = text(projectId);
            actor = text(actor);
            runId = text(runId);
            sessionId = text(sessionId);
            metadata = metadata == null || metadata.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        private static String text(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
