package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.repair.OpsControlledCodeToolService;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsCodeToolExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final OpsControlledCodeToolService codeTools;
    private final OpsRepairWorkspaceService repairWorkspaces;

    public OpsCodeToolExecutionDispatchHandler(
            ObjectProvider<OpsControlledCodeToolService> codeTools,
            ObjectProvider<OpsRepairWorkspaceService> repairWorkspaces) {
        this.codeTools = codeTools.getIfAvailable();
        this.repairWorkspaces = repairWorkspaces.getIfAvailable();
    }

    @Override
    public String handlerId() {
        return "code-repair";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return target.toolsetId().startsWith("code.")
                || "CODE_REPAIR".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        if (codeTools == null) throw new IllegalStateException("CODE_REPAIR adapter 未初始化");
        Map<String, Object> input = merged(request.arguments(), request.requestContext());
        String actor = request.actor();
        return switch (target.toolName()) {
            case "code_read" -> codeTools.read(input, actor);
            case "code_grep" -> codeTools.grep(input, actor);
            case "code_glob" -> codeTools.glob(input, actor);
            case "code_edit" -> codeTools.edit(input, actor);
            case "code_write" -> codeTools.write(input, actor);
            case "code_bash" -> codeTools.bash(input, actor);
            case "code_lsp" -> codeTools.lsp(input, actor);
            case "code_enter_worktree" -> codeTools.enterWorktree(input, actor);
            case "code_exit_worktree" -> codeTools.exitWorktree(input, actor);
            case "code_compute_diff" -> codeTools.computeRepairDiff(input, actor);
            case "code_commit_repair" -> codeTools.commitRepair(input, actor);
            case "ValidateCodeCandidate" -> validateCodeCandidate(input, actor);
            default -> throw new IllegalArgumentException("未知代码工具：" + target.toolName());
        };
    }

    private Map<String, Object> validateCodeCandidate(Map<String, Object> input, String actor) {
        if (repairWorkspaces == null) throw new IllegalStateException("Repair workspace adapter 未初始化");
        OpsRepairWorkspaceDTO workspace = repairWorkspaces.createAndVerify(
                OpsRepairWorkspaceRequestDTO.builder()
                        .projectId(required(input.get("projectId"), "ValidateCodeCandidate 必须提供 projectId"))
                        .serviceId(text(input.get("serviceId"), ""))
                        .environment(text(input.get("environment"), "dev"))
                        .summary(text(input.get("summary"), ""))
                        .unifiedDiff(text(input.get("unifiedDiff"), ""))
                        .baseCommit(text(input.get("baseCommit"), ""))
                        .build(),
                StringUtils.hasText(actor) ? actor : "ops-agent");
        return Map.of(
                "workspaceId", workspace.getWorkspaceId(),
                "status", workspace.getStatus(),
                "baseCommit", text(workspace.getBaseCommit(), ""),
                "verifiedCommit", text(workspace.getVerifiedCommit(), ""),
                "changedFiles", workspace.getChangedFiles() == null ? List.of() : workspace.getChangedFiles());
    }

    private Map<String, Object> merged(Map<String, Object> arguments, Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        for (String key : List.of(
                "projectId", "sessionId", "runId", "userId", "packageId", "packageVersion", "packageHash")) {
            if (request.containsKey(key)) result.putIfAbsent(key, request.get(key));
        }
        return result;
    }

    private String required(Object value, String message) {
        String normalized = text(value, "");
        if (!StringUtils.hasText(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }
}
