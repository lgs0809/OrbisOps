package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectWorkspaceProjection;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsProjectWorkspaceProjectionMapperTest {

    private final OpsProjectWorkspaceProjectionMapper mapper = new OpsProjectWorkspaceProjectionMapper();

    @Test
    void requestExtractsTypedProjectSkillsAndEvidenceWithoutCredentialPayload() {
        ProjectWorkspaceProjectionRequest request = mapper.request(
                project(),
                List.of(resource()),
                List.of(mcp()),
                List.of("project-skill"),
                List.of("global-skill"),
                List.of("kb-1"),
                List.of(),
                List.of());

        assertEquals("demo-project", request.project().projectId());
        assertEquals(List.of("dev", "prod"), request.project().environments());
        assertEquals("PROJECT_AUTHORIZED", request.projectSkills().get(0).scope());
        assertEquals("GLOBAL_ENABLED", request.enabledGlobalSkills().get(0).scope());
        assertEquals(List.of("kb-1"), request.knowledgeBaseIds());
        assertEquals(List.of("mysql", "prometheus"),
                request.evidenceSources().stream().map(ProjectWorkspaceProjectionRequest.EvidenceSource::type).toList());
        assertEquals(1, request.dataResourceCount());
        assertEquals(1, request.generatedMcpCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPreservesCompatibilityFieldsAndMasksRuntimeConfiguration() {
        ProjectWorkspaceProjectionRequest request = mapper.request(
                project(),
                List.of(resource()),
                List.of(mcp()),
                List.of("project-skill"),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        ProjectWorkspaceProjection projection = new ProjectWorkspaceProjection(
                request.project(),
                request.projectSkills(),
                request.enabledGlobalSkills(),
                request.knowledgeBaseIds(),
                request.projectKnowledgeBases(),
                request.enabledGlobalKnowledgeBases(),
                1,
                2,
                3,
                6,
                1,
                true,
                true,
                "READY",
                List.of(new ProjectWorkspaceProjection.DiagnosticScenario(
                        "GENERAL_DIAGNOSIS",
                        "综合故障诊断",
                        "description",
                        "prompt",
                        true,
                        "",
                        List.of())),
                "GENERAL_DIAGNOSIS",
                List.of(new ProjectWorkspaceProjection.OnboardingStep(
                        "resources", "接入资源", true, false)));

        Map<String, Object> detail = mapper.detail(projection, project(), List.of(resource()), List.of(mcp()));

        assertEquals(6, detail.get("resourceCount"));
        assertEquals("GENERAL_DIAGNOSIS", detail.get("recommendedScenarioId"));
        assertEquals("retained", detail.get("customExtension"));
        List<Map<String, Object>> resources = (List<Map<String, Object>>) detail.get("resources");
        Map<String, Object> credential = (Map<String, Object>) resources.get(0).get("credential");
        assertEquals("ops_reader", credential.get("username"));
        assertEquals("******", credential.get("passwordMasked"));
        assertFalse(credential.containsKey("password"));
        Map<String, Object> schema = (Map<String, Object>) resources.get(0).get("schema");
        List<Map<String, Object>> objects = (List<Map<String, Object>>) schema.get("objects");
        assertEquals(List.of("id", "status"), objects.get(0).get("columns"));

        List<Map<String, Object>> mcps = (List<Map<String, Object>>) detail.get("generatedMcps");
        Map<String, Object> transportConfig = (Map<String, Object>) mcps.get(0).get("transportConfig");
        assertFalse(transportConfig.containsKey("runtimeEnv"));
        Map<String, Object> transportCredential = (Map<String, Object>) transportConfig.get("credential");
        assertEquals("******", transportCredential.get("passwordMasked"));
        assertTrue(detail.containsKey("diagnosticScenarios"));
    }

    @Test
    void catalogContainsReadinessProjectionButNoResourceDetails() {
        ProjectWorkspaceProjectionRequest request = mapper.request(
                project(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        ProjectWorkspaceProjection projection = new ProjectWorkspaceProjection(
                request.project(), List.of(), List.of(), List.of(), List.of(), List.of(),
                0, 0, 0, 0, 0,
                false, false, "DEFAULT_AGENT_NOT_PUBLISHED",
                List.of(), "", List.of());

        Map<String, Object> catalog = mapper.catalog(projection);

        assertEquals("demo-project", catalog.get("projectId"));
        assertEquals("DEFAULT_AGENT_NOT_PUBLISHED", catalog.get("readinessReason"));
        assertFalse(catalog.containsKey("resources"));
        assertFalse(catalog.containsKey("generatedMcps"));
        assertFalse(catalog.containsKey("owner"));
        assertFalse(catalog.containsKey("customExtension"));
    }

    private Map<String, Object> project() {
        return Map.ofEntries(
                Map.entry("projectId", "demo-project"),
                Map.entry("name", "示例系统"),
                Map.entry("description", "description"),
                Map.entry("owner", "ops"),
                Map.entry("environments", List.of("dev", "prod")),
                Map.entry("defaultAgentId", "demo-project-agent"),
                Map.entry("skillIds", List.of("legacy-skill")),
                Map.entry("sharedMcpIds", List.of("shared-mcp")),
                Map.entry("enabled", true),
                Map.entry("customExtension", "retained"),
                Map.entry("createdAt", "2026-07-22T10:00:00"),
                Map.entry("updatedAt", "2026-07-22T10:00:00"));
    }

    private Map<String, Object> resource() {
        return Map.ofEntries(
                Map.entry("resourceId", "mysql-1"),
                Map.entry("projectId", "demo-project"),
                Map.entry("type", "mysql"),
                Map.entry("status", "ENABLED"),
                Map.entry("credential", Map.of(
                        "username", "ops_reader",
                        "passwordRef", "secret://mysql/password",
                        "password", "should-not-leak")),
                Map.entry("schema", Map.of(
                        "objects", List.of(Map.of("name", "orders", "columns", List.of("id", "status"))))),
                Map.entry("permission", Map.of("actions", List.of("SELECT"), "objects", List.of("orders"))));
    }

    private Map<String, Object> mcp() {
        return Map.ofEntries(
                Map.entry("mcpId", "prometheus-mcp"),
                Map.entry("mcpName", "Prometheus MCP"),
                Map.entry("resourceType", "prometheus"),
                Map.entry("status", "ENABLED"),
                Map.entry("allowedActions", List.of("QUERY_RANGE")),
                Map.entry("readOnly", true),
                Map.entry("transportConfig", Map.of(
                        "runtimeEnv", Map.of("TOKEN", "should-not-leak"),
                        "credential", Map.of("username", "metrics", "passwordRef", "secret://metrics"))));
    }
}
