package cn.lgs.orbisops.domain.mcp.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyAdminRecord;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;

import java.util.List;
import java.util.Optional;

public interface IMcpToolPolicyRepository {

    boolean available();

    List<McpToolPolicy> findAll(String projectId, int limit);

    List<McpToolPolicyAdminRecord> findAllForAdmin(String projectId, int limit);

    List<McpToolPolicy> findRuntimeExecutable(String projectId, String toolIdOrMcpId);

    Optional<McpToolPolicy> findById(String projectId, String policyId);

    Optional<McpToolPolicy> findActiveReviewed(String projectId, String mcpId, String toolName, String schemaHash);

    Optional<McpToolPolicy> findPendingSuggestion(String projectId, String mcpId, String toolName, String schemaHash);

    void save(McpToolPolicy policy);

    boolean saveSuggestionIfAbsent(McpToolPolicy policy);

    boolean updateReviewStatus(String projectId,
                               String policyId,
                               McpToolPolicyStatus status,
                               McpToolPolicyReviewStatus reviewStatus,
                               String actor,
                               String metadataJson);

    int markActivePoliciesStale(String projectId, String mcpId, String toolName, String newSchemaHash);

    int markDuplicatePendingSuggestionsStale(String projectId,
                                             String mcpId,
                                             String toolName,
                                             String schemaHash,
                                             String keepPolicyId);
}
