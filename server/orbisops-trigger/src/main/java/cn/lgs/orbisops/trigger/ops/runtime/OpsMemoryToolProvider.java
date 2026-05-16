package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Explicit Memory capabilities exposed to the normal Daily Agent. */
@Service
public final class OpsMemoryToolProvider {

    public static final String UPSERT_TOOL = "RememberMemory";
    public static final String SEARCH_TOOL = "SearchMemory";

    private final OpsToolExecutionService toolExecutionService;

    public OpsMemoryToolProvider(OpsToolExecutionService toolExecutionService) {
        if (toolExecutionService == null) {
            throw new IllegalArgumentException("TOOL_EXECUTION_SERVICE_REQUIRED");
        }
        this.toolExecutionService = toolExecutionService;
    }

    public List<ToolCallback> build(
            String projectId,
            String actor,
            String sessionId,
            String runId) {
        require(projectId, "PROJECT_ID_REQUIRED");
        require(actor, "MEMORY_ACTOR_REQUIRED");
        require(runId, "RUN_ID_REQUIRED");
        return List.of(
                remember(projectId, actor, sessionId, runId),
                search(projectId, actor, sessionId, runId));
    }

    private ToolCallback remember(String projectId, String actor, String sessionId, String runId) {
        Function<RememberInput, String> function = input -> {
            if (input == null || input.content() == null || input.content().trim().isBlank()) {
                throw new IllegalArgumentException("MEMORY_CONTENT_REQUIRED");
            }
            return JSON.toJSONString(execute(
                    projectId,
                    actor,
                    sessionId,
                    runId,
                    "memory_upsert",
                    Map.of("content", input.content().trim())));
        };
        return FunctionToolCallback.builder(UPSERT_TOOL, function)
                .description("Persist information only when the user explicitly asks to remember/save it for future runs. The current user message is already visible to this run, so do not re-read or reinject the new Memory.")
                .inputType(RememberInput.class)
                .build();
    }

    private ToolCallback search(String projectId, String actor, String sessionId, String runId) {
        Function<SearchInput, String> function = input -> {
            int limit = input == null ? 20 : Math.max(1, Math.min(input.limit(), 100));
            return JSON.toJSONString(execute(
                    projectId,
                    actor,
                    sessionId,
                    runId,
                    "memory_search",
                    Map.of("limit", limit)));
        };
        return FunctionToolCallback.builder(SEARCH_TOOL, function)
                .description("Search governed user/project/session Memory when previously stored information is needed beyond the context already assembled for this run.")
                .inputType(SearchInput.class)
                .build();
    }

    private Map<String, Object> execute(
            String projectId,
            String actor,
            String sessionId,
            String runId,
            String toolName,
            Map<String, Object> arguments) {
        return toolExecutionService.execute(Map.of(
                "projectId", projectId,
                "userId", actor,
                "sessionId", text(sessionId),
                "runId", runId,
                "executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name(),
                "toolsetId", "memory",
                "toolName", toolName,
                "arguments", arguments), actor);
    }

    private String require(String value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record RememberInput(String content) {
    }

    public record SearchInput(int limit) {
    }
}
