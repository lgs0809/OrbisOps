package cn.lgs.orbisops.trigger.ops.runtime;

import org.springaicommunity.tool.search.ToolSearchToolCallAdvisor;
import org.springaicommunity.tool.searcher.RegexToolSearcher;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Adapts the framework's discovery hooks to Alibaba's existing graph loop. Never calls adviseCall:
 * Graph remains the sole owner of tool execution, checkpoints, cancellation and investigation limits.
 * Each instance belongs to one agent invocation; rebuilding its local index replaces stale definitions.
 */
final class OpsMcpToolSearch extends ToolSearchToolCallAdvisor {
    private final String scope = UUID.randomUUID().toString();
    private String indexedSummaryHash;
    private java.util.Map<String, Object> frameworkContext;

    OpsMcpToolSearch(int limit) {
        this(limit, new KeywordSearcher(limit));
    }
    OpsMcpToolSearch(int limit, org.springaicommunity.tool.search.ToolSearcher searcher) {
        super(ToolCallingManager.builder().build(), 0, searcher,
                "\nUse toolSearchTool to discover MCP capabilities before invoking them. "
                        + "Use a short literal query of 1 to 80 characters; each search returns at most " + limit + " tools.", true, limit);
    }

    /** Use the framework index and ranking, but never execute model-supplied regular expressions. */
    private static final class KeywordSearcher extends RegexToolSearcher {
        private final int limit;
        private KeywordSearcher(int limit) { this.limit = limit; }
        @Override public org.springaicommunity.tool.search.ToolSearchResponse search(
                org.springaicommunity.tool.search.ToolSearchRequest request) {
            String query = request.query();
            if (query == null || query.isBlank() || query.length() > 80) {
                throw new IllegalArgumentException("MCP_SEARCH_QUERY_REQUIRES_1_TO_80_CHARACTERS");
            }
            // RegexToolSearcher already tokenizes and escapes every regex metacharacter.
            // The short bound also prevents its 200-character pattern truncation path.
            return super.search(new org.springaicommunity.tool.search.ToolSearchRequest(
                    request.sessionId(), query, limit, request.categoryFilter()));
        }
    }

    ToolCallingChatOptions disclose(List<ToolCallback> tools, List<Message> history) {
        // The advisor needs only genuine search observations. Do not copy Alibaba's private
        // instruction subtype through Spring's Prompt.copy, or turn user text into tool references.
        List<Message> discoveryHistory = new ArrayList<>();
        discoveryHistory.add(new SystemMessage("MCP discovery"));
        for (Message message : history) {
            if (!(message instanceof ToolResponseMessage result)) continue;
            var responses = result.getResponses().stream().filter(response -> {
                if (!"toolSearchTool".equals(response.name())) return false;
                try {
                    var values = com.alibaba.fastjson.JSON.parse(response.responseData());
                    return values instanceof java.util.List<?> list && list.stream().allMatch(String.class::isInstance);
                } catch (RuntimeException invalid) { return false; }
            }).toList();
            if (!responses.isEmpty()) discoveryHistory.add(ToolResponseMessage.builder().responses(responses).build());
        }
        var options = ToolCallingChatOptions.builder().toolCallbacks(tools).build();
        var context = new HashMap<String, Object>();
        context.put(ChatMemory.CONVERSATION_ID, scope);
        var request = ChatClientRequest.builder().prompt(new Prompt(discoveryHistory, options)).context(context).build();
        String summaryHash = cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(tools.stream()
                .map(ToolCallback::getToolDefinition).sorted(java.util.Comparator.comparing(org.springframework.ai.tool.definition.ToolDefinition::name))
                .map(definition -> java.util.Map.of("name", definition.name(), "description", definition.description())).toList());
        if (!summaryHash.equals(indexedSummaryHash)) {
            var initialized = doInitializeLoop(request, null);
            frameworkContext = new HashMap<>(initialized.context());
            indexedSummaryHash = summaryHash;
        }
        // 1.0.1 keeps resolved callbacks in the advisor context. Refresh contracts and
        // authorization on every round without reindexing unchanged name/description text.
        // This compatibility seam is exercised against the actual framework in tests.
        context.putAll(frameworkContext);
        context.put("cachedToolCallbacks", tools.stream().collect(java.util.stream.Collectors.toMap(
                tool -> tool.getToolDefinition().name(), java.util.function.Function.identity())));
        var current = ChatClientRequest.builder().prompt(request.prompt()).context(context).build();
        var disclosed = (ToolCallingChatOptions) doBeforeCall(current, null).prompt().getOptions();
        disclosed.setToolCallbacks(disclosed.getToolCallbacks().stream()
                .map(OpsMcpSearchContract::publish).toList());
        return disclosed;
    }
}
