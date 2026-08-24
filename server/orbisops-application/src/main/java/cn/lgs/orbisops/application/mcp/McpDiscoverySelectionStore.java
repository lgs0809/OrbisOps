package cn.lgs.orbisops.application.mcp;

/** First-writer selection for a trusted Run/agent scope. This stores no tool authority. */
public interface McpDiscoverySelectionStore {
    record Scope(String projectId, String runId, String agentId, String nodeId) {
        public Scope {
            if (projectId == null || projectId.isBlank() || runId == null || runId.isBlank()
                    || agentId == null || agentId.isBlank() || nodeId == null || nodeId.isBlank())
                throw new IllegalArgumentException("MCP_DISCOVERY_SCOPE_REQUIRED");
        }
    }
    record CountingBasis(String contractVersion, String implementation, String implementationVersion,
                         String encodingLibraryVersion, String encoding, String accuracy,
                         String modelId, String modelName, int summaryToolLimit, int summaryTokenLimit, int searchLimit) {
        public CountingBasis {
            if (!"mcp-summary-count/v1".equals(contractVersion)
                    || implementation == null || implementation.isBlank()
                    || implementationVersion == null || implementationVersion.isBlank()
                    || encodingLibraryVersion == null || encodingLibraryVersion.isBlank() || encoding == null || encoding.isBlank()
                    || !"ESTIMATE".equals(accuracy) || modelId == null || modelName == null
                    || summaryToolLimit < 0 || summaryTokenLimit < 0 || searchLimit < 1 || searchLimit > 5)
                throw new IllegalArgumentException("MCP_DISCOVERY_COUNTING_BASIS_INVALID");
        }
    }
    record Selection(String mode, int toolCount, int summaryTokens, String tokenizer, String catalogHash,
                     CountingBasis countingBasis) {
        /** Old rows have no certified counting basis; never invent one during recovery. */
        public Selection(String mode, int toolCount, int summaryTokens, String tokenizer, String catalogHash) {
            this(mode, toolCount, summaryTokens, tokenizer, catalogHash, null);
        }
        public Selection {
            if (!java.util.Set.of("SUMMARY", "SEARCH").contains(mode) || toolCount < 0 || summaryTokens < 0
                    || tokenizer == null || tokenizer.isBlank() || catalogHash == null || !catalogHash.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("MCP_DISCOVERY_SELECTION_INVALID");
        }
    }
    Selection freeze(Scope scope, Selection proposed);
}
