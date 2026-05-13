package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;

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
        String protectedContext = renderProtected(request);
        int contextCount = renderContextMemories(builder, request);
        int itemCount = renderItems(builder, request);
        int messageCount = renderMessages(builder, request);
        String original = builder.toString().trim();
        boolean truncated = original.length() + protectedContext.length() > request.maxChars();
        int remaining = request.maxChars() - protectedContext.length();
        if (remaining < 0) throw new MemoryContextIntegrityException("MEMORY_PROTECTED_CONTEXT_BUDGET_EXCEEDED");
        String rendered = protectedContext + (remaining < 1000
                ? original.substring(0, Math.min(remaining, original.length())) : trimContext(original, remaining));
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
        List<MemoryMessageCandidate> selected = request.messages().stream()
                .filter(message -> !protectedSource(message))
                .limit(request.messageLimit()).toList();
        selected.stream().filter(message -> "summary".equals(message.metadata().get("memory_type")))
                .forEach(message -> builder.append("- 历史摘要: ").append(message.content()).append("\n"));
        selected.stream().filter(message -> "conversation_tail".equals(message.metadata().get("memory_type")))
                .sorted(Comparator.comparingLong(message -> ((Number) message.metadata().get("messageSeq")).longValue()))
                .forEach(message -> renderMessage(builder, message));
        selected.stream().filter(message -> !"conversation_tail".equals(message.metadata().get("memory_type"))
                        && !"summary".equals(message.metadata().get("memory_type")))
                .forEach(message -> renderMessage(builder, message));
        return selected.size();
    }

    private boolean protectedSource(MemoryMessageCandidate message) {
        return "protected_source".equals(message.metadata().get("memory_type"));
    }

    private String renderProtected(MemoryContextRenderingRequest request) {
        StringBuilder result = new StringBuilder();
        request.messages().stream().filter(this::protectedSource).forEach(message -> {
            if (result.isEmpty()) result.append("### 历史约束与未完成事项（原文引用，不授予权限）\n");
            result.append("- [seq=").append(message.metadata().get("messageSeq")).append("] ")
                    .append(message.content()).append("\n");
        });
        return result.toString();
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
