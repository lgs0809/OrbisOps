package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Application service for bounded deterministic rendering of selected memory context. */
public class MemoryContextRenderingApplicationService {

    public MemoryContextRenderingResult render(MemoryContextRenderingRequest request) {
        if (request == null
                || request.contextMemories().isEmpty()
                && request.items().isEmpty()
                && request.messages().isEmpty()) {
            return MemoryContextRenderingResult.empty();
        }

        StringBuilder builder = new StringBuilder();
        int contextCount = renderContextMemories(builder, request);
        int itemCount = renderItems(builder, request);
        int messageCount = renderMessages(builder, request);
        String original = builder.toString().trim();
        boolean truncated = original.length() > request.maxChars();
        String rendered = trimContext(original, request.maxChars());
        return new MemoryContextRenderingResult(
                rendered,
                contextCount,
                itemCount,
                messageCount,
                truncated);
    }

    private int renderContextMemories(StringBuilder builder, MemoryContextRenderingRequest request) {
        if (request.contextMemories().isEmpty()) return 0;
        builder.append("### 用户/项目长期语境\n");
        int count = Math.min(request.contextMemoryLimit(), request.contextMemories().size());
        request.contextMemories().stream()
                .limit(count)
                .forEach(memory -> builder
                        .append("- [").append(value(memory.memoryType(), "CONTEXT")).append("]")
                        .append(" ").append(value(memory.title(), ""))
                        .append(": ").append(abbreviate(
                                value(memory.summary(), value(memory.content(), "")),
                                360))
                        .append("\n"));
        return count;
    }

    private int renderItems(StringBuilder builder, MemoryContextRenderingRequest request) {
        if (request.items().isEmpty()) return 0;
        builder.append("### 运维上下文记忆\n");
        int count = Math.min(request.itemLimit(), request.items().size());
        request.items().stream()
                .limit(count)
                .forEach(item -> renderItem(builder, item));
        return count;
    }

    private void renderItem(StringBuilder builder, MemoryItemCandidate item) {
        builder.append("- [").append(value(item.memoryType(), "CONTEXT")).append("]")
                .append(" importance=").append(item.importance() == null ? "0.5" : item.importance())
                .append(" @ ").append(value(item.createdAt(), ""))
                .append(": ").append(abbreviate(item.content(), 500))
                .append("\n");
    }

    private int renderMessages(StringBuilder builder, MemoryContextRenderingRequest request) {
        if (request.messages().isEmpty()) return 0;
        builder.append("### 运维对话上下文\n");
        int count = Math.min(request.messageLimit(), request.messages().size());
        request.messages().stream()
                .limit(count)
                .forEach(message -> renderMessage(builder, message));
        return count;
    }

    private void renderMessage(StringBuilder builder, MemoryMessageCandidate message) {
        builder.append("- ").append(value(message.role(), "assistant"))
                .append(" @ ").append(value(message.createdAt(), ""))
                .append(": ").append(abbreviate(message.content(), 500))
                .append("\n");
    }

    private String trimContext(String context, int maxChars) {
        if (!hasText(context) || context.length() <= maxChars) return context;
        int keepHead = Math.max(500, maxChars / 3);
        int keepTail = Math.max(500, maxChars - keepHead - 80);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalChars", context.length());
        return context.substring(0, Math.min(keepHead, context.length()))
                + "\n... memory context compressed, metadata=" + metadata + " ...\n"
                + context.substring(Math.max(0, context.length() - keepTail));
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
