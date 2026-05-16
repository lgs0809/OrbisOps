package cn.lgs.orbisops.trigger.ops.runtime;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Counts the complete serialized summary, never a character approximation or truncated prefix. */
@Component
public final class OpsMcpDiscoveryPolicy {
    private final cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore store;
    private final int summaryToolLimit;
    private final int summaryTokenLimit;
    private final int searchLimit;
    private final EncodingType encoding;
    private final JTokkitTokenCountEstimator tokenizer;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsMcpDiscoveryPolicy(
            @Value("${orbisops.progressive-mcp.discovery.summary-tool-limit:20}") int summaryToolLimit,
            @Value("${orbisops.progressive-mcp.discovery.summary-token-limit:2000}") int summaryTokenLimit,
            @Value("${orbisops.progressive-mcp.discovery.search-limit:5}") int searchLimit,
            @Value("${orbisops.progressive-mcp.discovery.tokenizer:O200K_BASE}") EncodingType encoding,
            cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore store) {
        if (summaryToolLimit < 0 || summaryTokenLimit < 0 || searchLimit < 1 || searchLimit > 5 || encoding == null) {
            throw new IllegalArgumentException("MCP_DISCOVERY_LIMITS_INVALID");
        }
        this.summaryToolLimit = summaryToolLimit;
        this.summaryTokenLimit = summaryTokenLimit;
        this.searchLimit = searchLimit;
        this.encoding = encoding;
        this.tokenizer = new JTokkitTokenCountEstimator(encoding);
        this.store = store;
    }

    OpsMcpDiscoveryPolicy(int toolLimit, int tokenLimit, int searchLimit, EncodingType encoding) {
        this(toolLimit, tokenLimit, searchLimit, encoding, null);
    }

    static OpsMcpDiscoveryPolicy defaults() { return new OpsMcpDiscoveryPolicy(20, 2000, 5, EncodingType.O200K_BASE); }
    Selection select(int count, String summaries) {
        int tokens = tokenizer.estimate(summaries);
        return new Selection(count <= summaryToolLimit && tokens <= summaryTokenLimit ? Mode.SUMMARY : Mode.SEARCH,
                count, tokens, encoding.name());
    }
    Selection select(OpsRuntimeResourceBundle bundle, int count, String summaries) {
        Selection proposed = select(count, summaries);
        if (store == null || bundle.getMcpServers().isEmpty()) return proposed;
        var scopes = bundle.getMcpServers().stream().map(server ->
                new cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.Scope(bundle.getProjectId(),
                        server.getRunId(), server.getAgentId(), server.getNodeId())).distinct().toList();
        if (scopes.size() != 1) throw new IllegalStateException("MCP_DISCOVERY_SCOPE_MISMATCH");
        var frozen = store.freeze(scopes.get(0), new cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.Selection(
                proposed.mode().name(), count, proposed.tokens(), proposed.tokenizer(),
                cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(
                        com.alibaba.fastjson.JSON.parse(summaries))));
        return new Selection(Mode.valueOf(frozen.mode()), frozen.toolCount(), frozen.summaryTokens(), frozen.tokenizer());
    }
    int searchLimit() { return searchLimit; }
    enum Mode { SUMMARY, SEARCH }
    record Selection(Mode mode, int tools, int tokens, String tokenizer) { }
}
