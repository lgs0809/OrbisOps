package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogPort;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillCatalogSnapshot;
import cn.lgs.orbisops.application.skill.SkillPackageQueryService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillEvalSuiteRunnerTest {

    private SkillCatalogQueryService query(SkillCatalogPort catalog) {
        return query(catalog, mock(SkillPackageQueryService.class));
    }

    private SkillCatalogQueryService query(SkillCatalogPort catalog,
                                           SkillPackageQueryService packageQueryService) {
        return new SkillCatalogQueryService(catalog, packageQueryService);
    }

    @Test
    void passesStructuredPackageEvalAgainstExactBaseline() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.getProjectEntry("p1", "diagnosis"))
                .thenReturn(snapshot(3, "base-hash", ""));
        OpsSkillEvalSuiteRunner runner = new OpsSkillEvalSuiteRunner(query(catalog));
        Map<String, Object> expected = Map.of(
                "patchType", "UPDATE_DIAGNOSTIC_RECIPE",
                "requiredChangeKeys", List.of("order-errors"),
                "requiredArtifactPaths", List.of("resources/method.json"),
                "requiresEvidence", true,
                "forbiddenPatterns", List.of("绕过审批"));
        Map<String, Object> candidate = Map.of(
                "project_id", "p1", "target_skill_id", "diagnosis", "patch_type", "UPDATE_DIAGNOSTIC_RECIPE",
                "base_skill_version", 3, "base_skill_hash", "base-hash",
                "changes_json", JSON.toJSONString(List.of(Map.of("section", "diagnosticRecipe",
                        "operation", "upsert", "key", "order-errors", "value", Map.of("readOnly", true)))),
                "artifacts_json", JSON.toJSONString(List.of(Map.of("path", "resources/method.json",
                        "role", "RESOURCE", "content", "{}"))),
                "eval_cases_json", JSON.toJSONString(List.of(Map.of("caseId", "case-1", "expected", expected))),
                "evidence_refs_json", JSON.toJSONString(List.of(Map.of("resultId", "r1", "outputHash", "h1"))));

        OpsSkillEvalSuiteRunner.Result result = runner.run(candidate);

        assertTrue(result.passed());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void failsClosedForBaselineConflictAndUnsafeCandidate() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        when(catalog.getProjectEntry("p1", "diagnosis"))
                .thenReturn(snapshot(4, "new-hash", ""));
        OpsSkillEvalSuiteRunner runner = new OpsSkillEvalSuiteRunner(query(catalog));
        Map<String, Object> candidate = Map.of(
                "project_id", "p1", "target_skill_id", "diagnosis", "patch_type", "UPDATE_DIAGNOSTIC_RECIPE",
                "base_skill_version", 3, "base_skill_hash", "base-hash",
                "changes_json", JSON.toJSONString(List.of(Map.of("section", "diagnosticRecipe",
                        "operation", "upsert", "key", "unsafe", "value", "绕过审批"))),
                "artifacts_json", "[]",
                "eval_cases_json", JSON.toJSONString(List.of(Map.of("caseId", "case-1",
                        "expected", Map.of("requiresEvidence", true)))),
                "evidence_refs_json", "[]");

        OpsSkillEvalSuiteRunner.Result result = runner.run(candidate);

        assertFalse(result.passed());
        assertTrue(result.failures().contains("BASELINE_MVCC_CONFLICT"));
        assertTrue(result.failures().stream().anyMatch(value -> value.startsWith("REGRESSION_UNSAFE_CONTENT")));
        assertTrue(result.failures().contains("EVAL_REQUIRED_EVIDENCE_MISSING"));
    }

    @Test
    void detectsRegressionByReplayingBaselineAndCandidateAgainstSameFixture() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        SkillPackageQueryService packageQuery = mock(SkillPackageQueryService.class);
        when(catalog.getProjectEntry("p1", "diagnosis"))
                .thenReturn(snapshot(3, "base-hash", "package-hash"));
        when(packageQuery.listArtifacts("p1", "diagnosis", 3,
                "base-hash", "package-hash", "PROJECT")).thenReturn(List.of(Map.of(
                "path", "resources/method.json",
                "role", "RESOURCE",
                "content", JSON.toJSONString(List.of(Map.of(
                        "section", "routingRules",
                        "operation", "upsert",
                        "key", "order-errors",
                        "value", Map.of("preferredSources", List.of("prometheus"))))))));
        OpsSkillEvalSuiteRunner runner = new OpsSkillEvalSuiteRunner(query(catalog, packageQuery));
        Map<String, Object> candidate = Map.of(
                "project_id", "p1",
                "target_skill_id", "diagnosis",
                "patch_type", "UPDATE_ROUTING_RULE",
                "base_skill_version", 3,
                "base_skill_hash", "base-hash",
                "changes_json", JSON.toJSONString(List.of(Map.of(
                        "section", "routingRules",
                        "operation", "delete",
                        "key", "order-errors"))),
                "artifacts_json", "[]",
                "eval_cases_json", JSON.toJSONString(List.of(Map.of(
                        "caseId", "keep-order-routing",
                        "expected", Map.of("requiredChangeKeys", List.of("order-errors"))))),
                "evidence_refs_json", JSON.toJSONString(List.of(Map.of("resultId", "r1", "outputHash", "h1"))));

        OpsSkillEvalSuiteRunner.Result result = runner.run(candidate);

        assertFalse(result.passed());
        assertTrue(result.failures().contains("BASELINE_TO_CANDIDATE_REGRESSION:keep-order-routing"));
        assertTrue(Boolean.TRUE.equals(result.caseResults().get(0).get("baselinePassed")));
        assertTrue(Boolean.TRUE.equals(result.caseResults().get(0).get("regression")));
    }

    @Test
    void enforcesWhenToUseAndWhenNotToUseRegressionCases() {
        SkillCatalogPort catalog = mock(SkillCatalogPort.class);
        OpsSkillEvalSuiteRunner runner =
                new OpsSkillEvalSuiteRunner(query(catalog));
        Map<String, Object> routingChange = Map.of(
                "section", "routingProfile",
                "operation", "upsert",
                "key", "routing",
                "value", Map.of(
                        "category", "DOCUMENT",
                        "subcategory", "presentation",
                        "whenToUse", List.of("制作季度汇报 PPT"),
                        "whenNotToUse", List.of("后端接口开发"),
                        "keywords", List.of("PPT", "汇报")));
        Map<String, Object> candidate = Map.of(
                "project_id", "p1",
                "patch_type", "CREATE_SKILL",
                "changes_json", JSON.toJSONString(List.of(routingChange)),
                "artifacts_json", "[]",
                "eval_cases_json", JSON.toJSONString(List.of(
                        Map.of(
                                "caseId", "positive",
                                "input", Map.of("query", "帮我制作季度汇报 PPT"),
                                "expected", Map.of(
                                        "routeShouldMatch", true,
                                        "requiresRoutingBoundary", true)),
                        Map.of(
                                "caseId", "negative",
                                "input", Map.of("query", "帮我开发后端接口"),
                                "expected", Map.of(
                                        "routeShouldMatch", false,
                                        "requiresRoutingBoundary", true)))),
                "evidence_refs_json", JSON.toJSONString(
                        List.of(Map.of(
                                "resultId", "r1",
                                "outputHash", "h1"))));

        OpsSkillEvalSuiteRunner.Result result = runner.run(candidate);

        assertTrue(result.passed(), String.valueOf(result.failures()));
    }

    private SkillCatalogSnapshot snapshot(int version, String skillHash, String packageHash) {
        return SkillCatalogSnapshot.fromView(Map.ofEntries(
                Map.entry("skillId", "diagnosis"),
                Map.entry("projectId", "p1"),
                Map.entry("scope", "PROJECT"),
                Map.entry("name", "Diagnosis"),
                Map.entry("description", "Collect evidence"),
                Map.entry("version", version),
                Map.entry("currentVersion", version),
                Map.entry("skillHash", skillHash),
                Map.entry("currentSkillHash", skillHash),
                Map.entry("currentPackageHash", packageHash),
                Map.entry("status", "ACTIVE"),
                Map.entry("updateMode", "AUTO"),
                Map.entry("content", "# Diagnosis")));
    }
}
