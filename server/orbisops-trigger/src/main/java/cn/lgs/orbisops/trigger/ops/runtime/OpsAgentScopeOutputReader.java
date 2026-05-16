package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Extracts meaningful AgentScope output and builds bounded diagnostics. */
final class OpsAgentScopeOutputReader {

    String stateOutput(OverAllState state, String outputKey) {
        if (state == null) return "";
        if (StringUtils.hasText(outputKey)) {
            String output = state.<Object>value(outputKey)
                    .map(this::messageText)
                    .filter(this::isMeaningfulText)
                    .orElse("");
            if (isMeaningfulText(output)) return output;
        }
        String messageOutput = lastAssistantMessageText(
                state.value("messages").orElse(null));
        if (isMeaningfulText(messageOutput)) return messageOutput;
        for (String candidate : List.of("final_answer", "output", "result")) {
            String output = state.<Object>value(candidate)
                    .map(this::messageText)
                    .filter(this::isMeaningfulText)
                    .orElse("");
            if (isMeaningfulText(output)) return output;
        }
        return "";
    }

    boolean isMeaningfulText(String text) {
        return StringUtils.hasText(normalizedText(text));
    }

    Map<String, String> stateValueTypes(OverAllState state) {
        if (state == null || state.data() == null) return Map.of();
        Map<String, String> types = new LinkedHashMap<>();
        state.data().forEach((key, value) -> types.put(
                key,
                value == null ? "null" : value.getClass().getName()));
        return types;
    }

    Map<String, Object> stateDiagnostics(OverAllState state, String outputKey) {
        if (state == null) return Map.of();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put(
                "output",
                messageDiagnostics(state.value(outputKey).orElse(null)));
        Object messages = state.value("messages").orElse(null);
        if (messages instanceof List<?> list) {
            diagnostics.put("messageCount", list.size());
            diagnostics.put(
                    "messages",
                    list.stream().map(this::messageDiagnostics).toList());
        } else {
            diagnostics.put("messages", messageDiagnostics(messages));
        }
        return diagnostics;
    }

    private String lastAssistantMessageText(Object value) {
        if (value instanceof List<?> list) {
            for (int index = list.size() - 1; index >= 0; index--) {
                Object item = list.get(index);
                if (item instanceof AssistantMessage message
                        && isMeaningfulText(message.getText())) {
                    return normalizedText(message.getText());
                }
                String nested = lastAssistantMessageText(item);
                if (isMeaningfulText(nested)) return nested;
            }
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of(
                    "messages", "message", "output", "content", "text")) {
                String nested = lastAssistantMessageText(map.get(key));
                if (isMeaningfulText(nested)) return nested;
            }
        }
        return "";
    }

    private String messageText(Object value) {
        return messageText(value, 0);
    }

    private String messageText(Object value, int depth) {
        if (value == null || depth > 5) return "";
        if (value instanceof AssistantMessage message) {
            return normalizedText(message.getText());
        }
        if (value instanceof Message message) {
            return normalizedText(message.getText());
        }
        if (value instanceof ChatResponse response
                && response.getResult() != null) {
            return messageText(response.getResult().getOutput(), depth + 1);
        }
        if (value instanceof CharSequence sequence) {
            return normalizedText(sequence.toString());
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> messageText(item, depth + 1))
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n\n"));
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of(
                    "final_answer",
                    "output",
                    "result",
                    "message",
                    "content",
                    "text",
                    "messages")) {
                String nested = messageText(map.get(key), depth + 1);
                if (StringUtils.hasText(nested)) return nested;
            }
        }
        return "";
    }

    private String normalizedText(String text) {
        if (!StringUtils.hasText(text)) return "";
        String normalized = text.trim();
        if ("null".equalsIgnoreCase(normalized)
                || "\"null\"".equalsIgnoreCase(normalized)
                || "[]".equals(normalized)
                || "{}".equals(normalized)) {
            return "";
        }
        return text;
    }

    private Map<String, Object> messageDiagnostics(Object value) {
        if (value == null) return Map.of("type", "null");
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("type", value.getClass().getName());
        if (value instanceof AssistantMessage message) {
            diagnostics.put(
                    "textChars",
                    message.getText() == null ? 0 : message.getText().length());
            diagnostics.put(
                    "toolCallCount",
                    message.getToolCalls() == null ? 0 : message.getToolCalls().size());
            diagnostics.put(
                    "metadataKeys",
                    message.getMetadata() == null
                            ? List.of()
                            : message.getMetadata().keySet());
        } else if (value instanceof Message message) {
            diagnostics.put(
                    "textChars",
                    message.getText() == null ? 0 : message.getText().length());
        }
        return diagnostics;
    }
}
