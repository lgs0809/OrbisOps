package cn.lgs.orbisops.trigger.ops.runtime;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Estimates the complete serialized summary with an explicitly identified encoding.
 * This is not the provider's model tokenizer; the estimate and its basis are frozen together.
 */
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
        return select(count, summaries, "", "");
    }
    private Selection select(int count, String summaries, String modelId, String modelName) {
        int tokens = tokenizer.estimate(summaries);
        return new Selection(count <= summaryToolLimit && tokens <= summaryTokenLimit ? Mode.SUMMARY : Mode.SEARCH,
                count, tokens, encoding.name(), new cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.CountingBasis(
                        "mcp-summary-count/v1", "org.springframework.ai.tokenizer.JTokkitTokenCountEstimator",
                        libraryVersion(JTokkitTokenCountEstimator.class), libraryVersion(EncodingType.class),
                        encoding.name(), "ESTIMATE", modelId, modelName, summaryToolLimit, summaryTokenLimit, searchLimit));
    }
    Selection select(OpsRuntimeResourceBundle bundle, int count, String summaries) {
        var options = bundle.getChatModel() == null ? null : bundle.getChatModel().getDefaultOptions();
        Selection proposed = select(count, summaries, bundle.getModelId() == null ? "" : bundle.getModelId(),
                options == null || options.getModel() == null ? "" : options.getModel());
        if (store == null || bundle.getMcpServers().isEmpty()) return proposed;
        var scopes = bundle.getMcpServers().stream().map(server ->
                new cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.Scope(bundle.getProjectId(),
                        server.getRunId(), server.getAgentId(), server.getNodeId())).distinct().toList();
        if (scopes.size() != 1) throw new IllegalStateException("MCP_DISCOVERY_SCOPE_MISMATCH");
        var frozen = store.freeze(scopes.get(0), new cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.Selection(
                proposed.mode().name(), count, proposed.tokens(), proposed.tokenizer(),
                cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(
                        com.alibaba.fastjson.JSON.parse(summaries)), proposed.countingBasis()));
        return new Selection(Mode.valueOf(frozen.mode()), frozen.toolCount(), frozen.summaryTokens(), frozen.tokenizer(), frozen.countingBasis());
    }
    int searchLimit() { return searchLimit; }
    private static String libraryVersion(Class<?> type) {
        String version = type.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "UNREPORTED" : version;
    }
    enum Mode { SUMMARY, SEARCH }
    record Selection(Mode mode, int tools, int tokens, String tokenizer,
                     cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.CountingBasis countingBasis) {
        int searchLimit(int legacyFallback) { return countingBasis == null ? legacyFallback : countingBasis.searchLimit(); }
    }
}
