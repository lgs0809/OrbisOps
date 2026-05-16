package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Publishes the existing bounded search contract without replacing framework execution. */
final class OpsMcpSearchContract {
    private OpsMcpSearchContract() { }

    static ToolCallback publish(ToolCallback delegate) {
        var original = delegate.getToolDefinition();
        if (!"toolSearchTool".equals(original.name())) return delegate;
        var schema = JSON.parseObject(original.inputSchema());
        var query = schema.getJSONObject("properties").getJSONObject("query");
        query.put("minLength", 1);
        query.put("maxLength", 80);
        query.put("description", "Short literal capability keywords, 1 to 80 characters. Do not copy a whole task or regular expression.");
        var definition = ToolDefinition.builder().name(original.name())
                .description(original.description() + " Query must contain 1 to 80 characters.")
                .inputSchema(schema.toJSONString()).build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            @Override public String call(String input) { return delegate.call(input); }
            @Override public String call(String input, ToolContext context) { return delegate.call(input, context); }
        };
    }
}
