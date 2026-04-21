package cn.lgs.orbisops.trigger.ops.repair;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class OpsRepairToolProvider {

    private final OpsToolExecutionService toolExecutionService;

    public OpsRepairToolProvider() {
        this((OpsToolExecutionService) null);
    }

    @Autowired
    public OpsRepairToolProvider(
            ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider) {
        this(toolExecutionServiceProvider == null
                ? null
                : toolExecutionServiceProvider.getIfAvailable());
    }

    OpsRepairToolProvider(OpsToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    public ToolCallback build(String projectId, String actor) {
        return build(projectId, actor, null);
    }

    public ToolCallback build(String projectId, String actor, OpsAgentChatRequest runtimeRequest) {
        Function<RepairCandidateInput, String> function = input -> {
            if (input == null) {
                throw new IllegalArgumentException("修复候选不能为空");
            }
            Map<String, Object> arguments = new java.util.LinkedHashMap<>();
            arguments.put("projectId", projectId);
            arguments.put("serviceId", input.getServiceId());
            arguments.put("environment", input.getEnvironment());
            arguments.put("summary", input.getSummary());
            arguments.put("unifiedDiff", input.getUnifiedDiff());
            arguments.put("baseCommit", input.getBaseCommit());
            return JSON.toJSONString(executeTool("ValidateCodeCandidate", projectId, actor, arguments, runtimeRequest));
        };
        return FunctionToolCallback.builder("ValidateCodeCandidate", function)
                .description("""
                        在当前项目登记的代码仓库中创建隔离 Git worktree，应用 unified diff，
                        并在无网络、受资源限制的 Docker 沙箱中运行项目服务预先配置的白名单构建测试。
                        通过后会固化 verified commit 和制品哈希，但不会修改主工作区、推送远端或部署服务。
                        只有在运行证据和代码证据明确指向代码缺陷时才使用；先读取线上 Commit 对应代码，
                        再生成最小补丁。工具返回 VERIFIED 只表示沙箱测试通过，生产变更仍必须由用户审批。
                        """)
                .inputType(RepairCandidateInput.class)
                .build();
    }

    public List<ToolCallback> buildCodeTools(String projectId, String actor) {
        return buildCodeTools(projectId, actor, null);
    }

    public List<ToolCallback> buildCodeTools(String projectId, String actor, OpsAgentChatRequest runtimeRequest) {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(build(projectId, actor, runtimeRequest));
        tools.add(mapTool("code_read", "code.read：读取已登记仓库或 repair worktree 内文件，返回行号并自动脱敏。", input -> executeTool("code_read", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_grep", "code.grep：在已登记仓库或 repair worktree 内搜索文本，返回文件、行号和脱敏片段。", input -> executeTool("code_grep", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_glob", "code.glob：按 glob 查找已登记仓库或 repair worktree 内文件。", input -> executeTool("code_glob", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_edit", "code.edit：在 repair worktree 内精确替换文件内容，要求先 code.read。", input -> executeTool("code_edit", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_write", "code.write：在 repair worktree 内创建或覆盖文件，覆盖前要求先 code.read。", input -> executeTool("code_write", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_bash", "code.bash：受控 Bash，仅允许白名单只读、测试构建或 repair worktree 写操作。", input -> executeTool("code_bash", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_lsp", "code.lsp：只读代码智能查询；未配置语言服务器时返回 LSP_NOT_CONFIGURED。", input -> executeTool("code_lsp", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_enter_worktree", "code.enter_worktree：基于已登记仓库和 baseCommit 创建受控 repair worktree。", input -> executeTool("code_enter_worktree", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_exit_worktree", "code.exit_worktree：退出当前 repair worktree 上下文，不删除工作区。", input -> executeTool("code_exit_worktree", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_compute_diff", "computeRepairDiff：计算 repair worktree 相对 baseCommit 的 changedFiles/diffHash。", input -> executeTool("code_compute_diff", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("code_commit_repair", "commitRepair：只在 repair worktree 内提交 repair commit，并返回 diffHash/repairCommit。", input -> executeTool("code_commit_repair", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("tool_result_read", "tool_result.read：按权限读取工具结果切片。", input -> executeToolResult("tool_result_read", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("tool_result_grep", "tool_result.grep：按权限搜索工具结果。", input -> executeToolResult("tool_result_grep", projectId, actor, input, runtimeRequest)));
        tools.add(mapTool("tool_result_slice", "tool_result.slice：按权限切片工具结果。", input -> executeToolResult("tool_result_slice", projectId, actor, input, runtimeRequest)));
        return tools;
    }

    private ToolCallback mapTool(String name, String description, Function<Map<String, Object>, Map<String, Object>> function) {
        Function<Map<String, Object>, String> callback = input -> JSON.toJSONString(function.apply(input == null ? Map.of() : input));
        return FunctionToolCallback.builder(name, callback)
                .description(description)
                .inputType(Map.class)
                .build();
    }

    private Map<String, Object> withProject(Map<String, Object> input, String projectId) {
        java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>(input == null ? Map.of() : input);
        if (StringUtils.hasText(projectId)) {
            data.putIfAbsent("projectId", projectId);
        }
        return data;
    }

    private Map<String, Object> executeTool(String toolName, String projectId, String actor,
                                            Map<String, Object> input, OpsAgentChatRequest runtimeRequest) {
        if (toolExecutionService == null) {
            throw new SecurityException("OpsToolExecutionService 未初始化，代码工具不能绕过统一 ToolsetRouter。");
        }
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("userId", actor);
        request.put("executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name());
        request.put("toolsetId", codeToolset(toolName));
        request.put("toolName", toolName);
        request.put("arguments", withProject(input, projectId));
        bindRuntimeContext(request, runtimeRequest);
        return toolExecutionService.execute(request, actor);
    }

    private Map<String, Object> executeToolResult(String toolName, String projectId, String actor,
                                                  Map<String, Object> input, OpsAgentChatRequest runtimeRequest) {
        if (toolExecutionService == null) {
            throw new SecurityException("OpsToolExecutionService 未初始化，ToolResult 工具不能绕过统一 ToolsetRouter。");
        }
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("userId", actor);
        request.put("executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name());
        request.put("toolsetId", "tool_result");
        request.put("toolName", toolName);
        request.put("arguments", withProject(input, projectId));
        bindRuntimeContext(request, runtimeRequest);
        return toolExecutionService.execute(request, actor);
    }

    private void bindRuntimeContext(Map<String, Object> target, OpsAgentChatRequest runtimeRequest) {
        if (runtimeRequest == null) return;
        target.put("runId", value(runtimeRequest.getRunId()));
        target.put("sessionId", value(runtimeRequest.getSessionId()));
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (runtimeRequest.getMetadata() != null) {
            for (String key : List.of("_workSessionAttemptId", "_workSessionLeaseToken",
                    "_workSessionFencingToken", "runManifestHash", "executionHarness")) {
                if (runtimeRequest.getMetadata().containsKey(key)) metadata.put(key, runtimeRequest.getMetadata().get(key));
            }
        }
        target.put("metadata", metadata);
    }

    private String codeToolset(String toolName) {
        return switch (toolName) {
            case "code_read", "code_grep", "code_glob" -> "code.repository";
            default -> "code.repair";
        };
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public static final class RepairCandidateInput {

        private String serviceId;
        private String environment;
        private String summary;
        private String baseCommit;
        private String unifiedDiff;

        public RepairCandidateInput() {
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getBaseCommit() {
            return baseCommit;
        }

        public void setBaseCommit(String baseCommit) {
            this.baseCommit = baseCommit;
        }

        public String getUnifiedDiff() {
            return unifiedDiff;
        }

        public void setUnifiedDiff(String unifiedDiff) {
            this.unifiedDiff = unifiedDiff;
        }
    }
}
