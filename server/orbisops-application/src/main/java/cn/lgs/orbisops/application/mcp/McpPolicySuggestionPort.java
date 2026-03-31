package cn.lgs.orbisops.application.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface McpPolicySuggestionPort {

    Optional<McpPolicySuggestion> suggest(SuggestionRequest request);

    record SuggestionRequest(String projectId,
                             String mcpId,
                             String toolId,
                             String toolName,
                             String schemaHash,
                             boolean metadataComplete,
                             Map<String, Object> toolSnapshot) {
        public SuggestionRequest {
            projectId = text(projectId);
            mcpId = text(mcpId);
            toolId = text(toolId);
            toolName = text(toolName);
            schemaHash = text(schemaHash);
            toolSnapshot = toolSnapshot == null || toolSnapshot.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(toolSnapshot));
        }

        private static String text(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
