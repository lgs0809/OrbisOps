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
    record Selection(String mode, int toolCount, int summaryTokens, String tokenizer, String catalogHash) {
        public Selection {
            if (!java.util.Set.of("SUMMARY", "SEARCH").contains(mode) || toolCount < 0 || summaryTokens < 0
                    || tokenizer == null || tokenizer.isBlank() || catalogHash == null || !catalogHash.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("MCP_DISCOVERY_SELECTION_INVALID");
        }
    }
    Selection freeze(Scope scope, Selection proposed);
}
