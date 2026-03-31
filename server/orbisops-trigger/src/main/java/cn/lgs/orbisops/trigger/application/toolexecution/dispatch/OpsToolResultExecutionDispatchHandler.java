package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.evidence.OpsToolResultMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class OpsToolResultExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final ToolResultApplicationService results;
    private final OpsToolResultMapper mapper;

    public OpsToolResultExecutionDispatchHandler(
            ToolResultApplicationService results,
            OpsToolResultMapper mapper) {
        this.results = results;
        this.mapper = mapper;
    }

    @Override
    public String handlerId() {
        return "tool-result";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "tool_result".equals(target.toolsetId());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        Map<String, Object> arguments = request.arguments();
        String resultId = required(arguments.get("resultId"), "读取 ToolResult 必须提供 resultId");
        String projectId = text(first(request.requestContext().get("projectId"), arguments.get("projectId")));
        String userId = text(first(request.requestContext().get("userId"), arguments.get("userId")));
        return switch (target.toolName()) {
            case "tool_result_read" -> mapper.view(results.read(
                    resultId, projectId, userId,
                    intValue(arguments.get("offset"), 0), intValue(arguments.get("limit"), 100)));
            case "tool_result_grep" -> mapper.view(results.grep(
                    resultId, projectId, userId,
                    required(arguments.get("pattern"), "tool_result_grep 必须提供 pattern")));
            case "tool_result_slice" -> mapper.view(results.slice(
                    resultId, projectId, userId,
                    intValue(arguments.get("startLine"), 1), intValue(arguments.get("endLine"), 100)));
            default -> throw new IllegalArgumentException("未知 ToolResult 工具：" + target.toolName());
        };
    }

    private Object first(Object first, Object second) {
        return first != null ? first : second;
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (!StringUtils.hasText(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
