package cn.lgs.orbisops.compat;

import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

/** Enforces the server limit even when model arguments or an index exceed it. */
public final class BoundedToolIndex implements ToolIndex {
    private final ToolIndex delegate;
    private final int limit;

    public BoundedToolIndex(ToolIndex delegate, int limit) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
        if (limit < 1) throw new IllegalArgumentException("Positive result limit required");
        this.limit = limit;
    }

    @Override public void indexTool(String sessionId, ToolReference reference) {
        delegate.indexTool(sessionId, reference);
    }

    @Override public ToolSearchResponse search(ToolSearchRequest request) {
        int requested = request.maxResults() == null ? limit : request.maxResults();
        if (requested < 1) throw new IllegalArgumentException("Positive result count required");
        int bounded = Math.min(requested, limit);
        var response = delegate.search(new ToolSearchRequest(request.sessionId(), request.query(),
                bounded, request.categoryFilter()));
        return new ToolSearchResponse(response.toolReferences().stream().limit(bounded).toList(),
                response.totalMatches(), response.searchMetadata());
    }

    @Override public void clearIndex(String sessionId) { delegate.clearIndex(sessionId); }
}
