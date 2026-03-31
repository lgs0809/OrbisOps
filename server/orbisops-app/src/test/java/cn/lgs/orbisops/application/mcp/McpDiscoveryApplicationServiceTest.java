package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpDiscoveryApplicationServiceTest {

    private McpProjectDirectoryPort projects;
    private McpProjectToolCatalogPort catalog;
    private SelectRuntimeMcpToolsQuery runtimeTools;
    private IMcpRuntimeCatalogRepository runtimeCatalog;
    private McpToolSnapshotStorePort snapshots;
    private IMcpToolPolicyRepository policies;
    private McpRuntimeAuditPort audit;
    private McpPolicySuggestionPort suggestions;
    private McpProjectToolDescriptor definition;

    @BeforeEach
    void setUp() {
        projects = mock(McpProjectDirectoryPort.class);
        catalog = mock(McpProjectToolCatalogPort.class);
        runtimeTools = mock(SelectRuntimeMcpToolsQuery.class);
        runtimeCatalog = mock(IMcpRuntimeCatalogRepository.class);
        snapshots = mock(McpToolSnapshotStorePort.class);
        policies = mock(IMcpToolPolicyRepository.class);
        audit = mock(McpRuntimeAuditPort.class);
        suggestions = mock(McpPolicySuggestionPort.class);
        useTool(Map.of());
        when(projects.exists("project-1")).thenReturn(true);
        when(runtimeCatalog.available()).thenReturn(true);
        when(snapshots.available()).thenReturn(true);
        when(policies.available()).thenReturn(true);
        when(policies.findActiveReviewed(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(policies.saveSuggestionIfAbsent(any())).thenReturn(true);
        when(snapshots.latestHydratedDefinition(
                anyString(), anyString(), anyString(), eq(20)))
                .thenReturn(Optional.empty());
        when(suggestions.suggest(any())).thenReturn(Optional.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectRanksAuthorizedRuntimeToolsAndPersistsDecision() {
        when(runtimeTools.runtimeExecutableTools("project-1", "mcp-1", List.of(), List.of(), "PREPARE"))
                .thenReturn(List.of(Map.of("toolName", "query_range")));
        Map<String, Object> requestPayload = Map.of(
                "capability", "metric_query",
                "userRequest", "查询 Prometheus 指标",
                "agentId", "agent-1",
                "runId", "run-1",
                "limit", 1);
        McpDiscoverySelectionRequest command = new McpDiscoverySelectionRequest(
                "project-1", "metric_query", "", 1,
                "agent-1", "", "run-1", "", "查询 Prometheus 指标",
                false, "", requestPayload);

        McpDiscoverySelectionResult result = service().select(command);
        Map<String, Object> view = result.view(requestPayload);

        assertEquals("SELECTED", result.status());
        assertEquals(1, result.selectedTools().size());
        assertEquals("mcp-1", result.selectedTools().get(0).toolId());
        verify(runtimeCatalog).saveRoutingDecision(any());
        verify(audit).recordRuntimeEvent("project-1", "agent-1", "", "tool-router",
                "select", result.decisionId(), "LOW", "SUCCEEDED", view);
    }

    @Test
    void unknownRemoteToolFailsClosedWithoutAuthoritativeOrPersistedSchema() {
        useTool(Map.of());

        SecurityException error = assertThrows(SecurityException.class, () -> service().hydrateSchema(
                hydration("deleteKeys", McpAuthoritativeToolDefinition.empty())));

        assertTrue(error.getMessage().contains("MCP_TOOL_SCHEMA_NOT_HYDRATED"));
        verify(runtimeCatalog, never()).saveSchemaCache(any());
        verify(snapshots, never()).save(any());
    }

    @Test
    void transportMetadataCannotSubstituteForAuthoritativeInputSchema() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));

        SecurityException error = assertThrows(SecurityException.class, () -> service().hydrateSchema(
                hydration("query_slow_log", McpAuthoritativeToolDefinition.empty())));

        assertTrue(error.getMessage().contains("MCP_TOOL_SCHEMA_NOT_HYDRATED"));
    }

    @Test
    void authoritativeRemoteDefinitionPersistsSnapshotPolicyAndCache() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"),
                        "description", "read slow query logs")));

        Map<String, Object> schema = service().hydrateSchema(hydration(
                "query_slow_log",
                new McpAuthoritativeToolDefinition(
                        "authoritative definition",
                        Map.of("type", "object", "properties",
                                Map.of("limit", Map.of("type", "integer"))),
                        "REMOTE_MCP_TOOL_DEFINITION"))).view();

        assertEquals(true, schema.get("schemaHydrated"));
        assertEquals("REMOTE_MCP_TOOL_DEFINITION", schema.get("schemaSource"));
        assertEquals("PENDING_REVIEW", schema.get("policyStatus"));
        assertEquals("LOW", schema.get("riskLevel"));
        assertEquals(true, schema.get("readOnly"));
        assertFalse(String.valueOf(schema.get("schemaHash")).isBlank());
        verify(snapshots).save(any());
        verify(policies).saveSuggestionIfAbsent(any());
        verify(suggestions).suggest(any());
        verify(runtimeCatalog).saveSchemaCache(any());
        verify(audit).recordRuntimeEvent(eq("project-1"), eq("agent-1"), eq(""), eq("tool-router"),
                eq("hydrate-schema"), anyString(), eq("LOW"), eq("SUCCEEDED"), any());
    }

    @Test
    void platformGeneratedReadonlyToolIsSystemVerifiedWithoutLlmReview() {
        useTool(Map.of(
                "openapi_list_operations", Map.of(
                        "remoteToolName", "openapi_list_operations",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ_OPENAPI"),
                        "capability", "DISCOVERY",
                        "platformGenerated", true,
                        "description", "list project OpenAPI operations")));

        Map<String, Object> schema = service().hydrateSchema(hydration(
                "openapi_list_operations",
                new McpAuthoritativeToolDefinition(
                        "authoritative OpenAPI discovery definition",
                        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                        "REMOTE_MCP_TOOL_DEFINITION"))).view();

        assertEquals("ACTIVE", schema.get("policyStatus"));
        assertEquals("SYSTEM_VERIFIED", schema.get("reviewStatus"));
        assertEquals(false, schema.get("requiresApprovedPackage"));
        assertEquals(false, schema.get("requiresHumanApproval"));
        assertEquals("CORE", schema.get("disclosureTier"));
        ArgumentCaptor<McpToolPolicy> saved = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(policies).save(saved.capture());
        assertEquals(McpToolPolicyStatus.ACTIVE, saved.getValue().status());
        assertEquals(McpToolPolicyReviewStatus.SYSTEM_VERIFIED, saved.getValue().reviewStatus());
        verify(suggestions, never()).suggest(any());
        verify(policies, never()).findPendingSuggestion(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void repeatedHydrationReusesExistingPendingSuggestionWithoutSecondSave() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        McpAuthoritativeToolDefinition authoritative = new McpAuthoritativeToolDefinition(
                "authoritative definition",
                Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer"))),
                "REMOTE_MCP_TOOL_DEFINITION");
        McpDiscoveryApplicationService service = service();

        service.hydrateSchema(hydration("query_slow_log", authoritative));
        ArgumentCaptor<McpToolPolicy> saved = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(policies).saveSuggestionIfAbsent(saved.capture());
        McpToolPolicy canonical = saved.getValue();
        when(policies.findPendingSuggestion(
                "project-1", "mcp-1", "query_slow_log", canonical.schemaHash()))
                .thenReturn(Optional.of(canonical));

        service.hydrateSchema(hydration("query_slow_log", authoritative));

        verify(policies, times(1)).saveSuggestionIfAbsent(any());
        verify(policies, times(2)).markDuplicatePendingSuggestionsStale(
                "project-1", "mcp-1", "query_slow_log", canonical.schemaHash(), canonical.policyId());
    }

    @Test
    void concurrentSuggestionConflictReturnsAuthoritativeReviewedPolicyWithoutDowngrade() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        McpToolPolicy reviewed = new McpToolPolicy(
                9L, "mcp-policy-reviewed", "project-1", "mcp-1", "mcp-1", "query_slow_log",
                "schema-reviewed", "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY", "READ_ONLY",
                "[\"READ\"]", McpRiskLevel.LOW,
                true, true, true, false, false, false, false, false, "{}",
                McpToolPolicyStatus.ACTIVE, McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", null, "", null, "{}", null, null);
        when(policies.saveSuggestionIfAbsent(any())).thenReturn(false);
        when(policies.findActiveReviewed(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty(), Optional.of(reviewed));

        Map<String, Object> result = service().hydrateSchema(hydration(
                "query_slow_log", new McpAuthoritativeToolDefinition(
                        "authoritative definition", Map.of("type", "object"),
                        "REMOTE_MCP_TOOL_DEFINITION"))).view();

        assertEquals("ACTIVE", result.get("policyStatus"));
        assertEquals("mcp-policy-reviewed", result.get("policyId"));
        verify(policies, never()).save(any());
    }

    @Test
    void sameSchemaUsesSameDeterministicSuggestionIdentity() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        McpAuthoritativeToolDefinition authoritative = new McpAuthoritativeToolDefinition(
                "authoritative definition", Map.of("type", "object"),
                "REMOTE_MCP_TOOL_DEFINITION");
        McpDiscoveryApplicationService service = service();

        service.hydrateSchema(hydration("query_slow_log", authoritative));
        service.hydrateSchema(hydration("query_slow_log", authoritative));

        ArgumentCaptor<McpToolPolicy> saved = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(policies, times(2)).saveSuggestionIfAbsent(saved.capture());
        assertEquals(saved.getAllValues().get(0).policyId(), saved.getAllValues().get(1).policyId());
        assertEquals(saved.getAllValues().get(0).schemaHash(), saved.getAllValues().get(1).schemaHash());
    }

    @Test
    void schemaChangeProducesNewDeterministicPolicyIdentityAndStalesPriorSchema() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        McpDiscoveryApplicationService service = service();

        service.hydrateSchema(hydration("query_slow_log", new McpAuthoritativeToolDefinition(
                "authoritative definition", Map.of("type", "object"),
                "REMOTE_MCP_TOOL_DEFINITION")));
        service.hydrateSchema(hydration("query_slow_log", new McpAuthoritativeToolDefinition(
                "authoritative definition", Map.of("type", "object", "required", List.of("limit")),
                "REMOTE_MCP_TOOL_DEFINITION")));

        ArgumentCaptor<McpToolPolicy> saved = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(policies, times(2)).saveSuggestionIfAbsent(saved.capture());
        McpToolPolicy first = saved.getAllValues().get(0);
        McpToolPolicy second = saved.getAllValues().get(1);
        assertNotEquals(first.schemaHash(), second.schemaHash());
        assertNotEquals(first.policyId(), second.policyId());
        verify(policies).markActivePoliciesStale(
                "project-1", "mcp-1", "query_slow_log", second.schemaHash());
    }

    @Test
    void outputContractChangeAloneMustInvalidateReviewedPolicy() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        McpDiscoveryApplicationService service = service();

        service.hydrateSchema(hydration("query_slow_log", new McpAuthoritativeToolDefinition(
                "authoritative definition", Map.of("type", "object"), Map.of("type", "object", "required", List.of("count")),
                "REMOTE_MCP_TOOL_DEFINITION")));
        service.hydrateSchema(hydration("query_slow_log", new McpAuthoritativeToolDefinition(
                "authoritative definition", Map.of("type", "object"), Map.of("type", "object", "required", List.of("count", "version")),
                "REMOTE_MCP_TOOL_DEFINITION")));

        ArgumentCaptor<McpToolPolicy> saved = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(policies, times(2)).saveSuggestionIfAbsent(saved.capture());
        McpToolPolicy first = saved.getAllValues().get(0);
        McpToolPolicy second = saved.getAllValues().get(1);
        assertNotEquals(first.schemaHash(), second.schemaHash());
        assertNotEquals(first.policyId(), second.policyId());
        verify(policies).markActivePoliciesStale(
                "project-1", "mcp-1", "query_slow_log", second.schemaHash());
    }

    @Test
    void persistedAuthoritativeSchemaCanBeReused() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        when(snapshots.latestHydratedDefinition(
                "project-1", "mcp-1", "query_slow_log", 20))
                .thenReturn(Optional.of(new McpAuthoritativeToolDefinition(
                        "persisted definition",
                        Map.of("type", "object"),
                        "PERSISTED_REMOTE_MCP_TOOL_DEFINITION")));

        Map<String, Object> schema = service().hydrateSchema(hydration(
                "query_slow_log", McpAuthoritativeToolDefinition.empty())).view();

        assertEquals(true, schema.get("schemaHydrated"));
        assertEquals("PERSISTED_REMOTE_MCP_TOOL_DEFINITION", schema.get("schemaSource"));
        assertEquals(Map.of("type", "object"), schema.get("schema"));
    }

    @Test
    void snapshotStoreUnavailableFailsClosedBeforePolicyAndCacheWrites() {
        useTool(Map.of(
                "query_slow_log", Map.of(
                        "remoteToolName", "query_slow_log",
                        "readOnly", true,
                        "riskLevel", "LOW",
                        "allowedActions", List.of("READ"))));
        when(snapshots.available()).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service().hydrateSchema(
                hydration("query_slow_log", new McpAuthoritativeToolDefinition(
                        "", Map.of("type", "object"),
                        "REMOTE_MCP_TOOL_DEFINITION"))));

        assertEquals("MCP_TOOL_SNAPSHOT_STORE_UNAVAILABLE", error.getMessage());
        verify(policies, never()).saveSuggestionIfAbsent(any());
        verify(runtimeCatalog, never()).saveSchemaCache(any());
    }

    @Test
    void schemaHashIgnoresRiskMetadataButChangesWithAuthoritativeSchema() {
        McpDiscoveryModelMapper mapper = new McpDiscoveryModelMapper(new McpJsonCodec());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("resourceType", "mysql");
        first.put("transportType", "stdio");
        first.put("description", "query");
        first.put("schema", Map.of("properties", Map.of("sql", Map.of("type", "string")), "type", "object"));
        first.put("riskLevel", "LOW");
        first.put("readOnly", true);
        Map<String, Object> second = new LinkedHashMap<>(first);
        second.put("riskLevel", "HIGH");
        second.put("readOnly", false);

        String firstHash = mapper.schemaHash("project-1", "mcp-1", "mcp-1", "query", first, Map.of());
        String secondHash = mapper.schemaHash("project-1", "mcp-1", "mcp-1", "query", second, Map.of());
        Map<String, Object> changed = new LinkedHashMap<>(first);
        changed.put("schema", Map.of("type", "object", "required", List.of("sql")));
        String changedHash = mapper.schemaHash("project-1", "mcp-1", "mcp-1", "query", changed, Map.of());

        assertEquals(firstHash, secondHash);
        assertNotEquals(firstHash, changedHash);
    }

    private McpDiscoveryApplicationService service() {
        return new McpDiscoveryApplicationService(projects, catalog, runtimeTools, runtimeCatalog,
                snapshots, policies, new McpJsonCodec(), audit, suggestions,
                Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC));
    }

    private McpSchemaHydrationRequest hydration(
            String remoteToolName,
            McpAuthoritativeToolDefinition authoritativeDefinition) {
        return new McpSchemaHydrationRequest(
                "project-1", "mcp-1", remoteToolName,
                "agent-1", "", authoritativeDefinition);
    }

    @SuppressWarnings("unchecked")
    private void useTool(Map<String, Object> remoteToolMetadata) {
        Map<String, McpRemoteToolDescriptor> remoteTools = new LinkedHashMap<>();
        remoteToolMetadata.forEach((name, value) -> {
            Map<String, Object> metadata = value instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            remoteTools.put(name, new McpRemoteToolDescriptor(
                    name,
                    String.valueOf(metadata.getOrDefault("description", "")),
                    (List<String>) metadata.getOrDefault(
                            "allowedActions", List.of("UNKNOWN_MUTATING")),
                    McpRiskLevel.valueOf(String.valueOf(
                            metadata.getOrDefault("riskLevel", "HIGH"))),
                    Boolean.TRUE.equals(metadata.get("readOnly")),
                    !metadata.isEmpty(),
                    metadata));
        });
        definition = new McpProjectToolDescriptor(
                "project-1", "mcp-1", "prometheus-query",
                "prometheus", "stdio", List.of("READ"),
                McpRiskLevel.LOW, true, Map.of(), 30,
                ProjectMcpStatus.ENABLED, remoteTools);
        when(catalog.list("project-1")).thenReturn(List.of(definition));
        when(catalog.find("project-1", "mcp-1"))
                .thenReturn(Optional.of(definition));
    }

}
