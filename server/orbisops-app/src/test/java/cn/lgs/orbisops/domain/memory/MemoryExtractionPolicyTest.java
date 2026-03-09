package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryExtractionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExtractionPolicyTest {

    private final MemoryExtractionPolicy policy = new MemoryExtractionPolicy();

    @Test
    void classifiesProjectContextAndUserWorkflowInHistoricalOrder() {
        ColdMemoryMessageSnapshot message = message(
                "记住以后默认先给结论再给依据；demo-project 项目目标是外挂型运维 Agent，不是替代原有运维平台。");

        List<MemoryExtractionDraft> drafts = policy.ruleDrafts(message);

        assertEquals(List.of("PROJECT_CONTEXT", "USER_WORKFLOW"),
                drafts.stream().map(MemoryExtractionDraft::memoryType).toList());
        assertEquals("项目背景", drafts.get(0).title());
        assertEquals("用户工作流", drafts.get(1).title());
    }

    @Test
    void classifiesGlossaryConventionPreferenceAndDomainFocus() {
        assertEquals("PROJECT_GLOSSARY", policy.ruleDrafts(message("项目术语 Ontology 指的是业务对象语义模型。"))
                .get(0).memoryType());
        assertEquals("PROJECT_CONVENTION", policy.ruleDrafts(message("demo-project 项目统一称执行包为 ChangePackage。"))
                .get(0).memoryType());
        assertEquals("USER_PREFERENCE", policy.ruleDrafts(message("以后优先给表格。"))
                .get(0).memoryType());
        assertEquals("USER_DOMAIN_FOCUS", policy.ruleDrafts(message("以后长期关注方向是 Agent DDD。"))
                .get(0).memoryType());
    }

    @Test
    void rejectsHardPolicyAndTransientEvidenceBeforeClassification() {
        assertTrue(policy.ruleDrafts(message(
                "记住以后所有 HIGH 变更必须审批，HTTP status 409 要返回给前端，沙箱必须验证。"))
                .isEmpty());
        assertTrue(policy.ruleDrafts(message(
                "记住 traceId=abc-123，Redis key sample=order:1，Docker containerId=778899。"))
                .isEmpty());
        assertTrue(policy.ruleDrafts(new ColdMemoryMessageSnapshot(
                "s1", "u1", "assistant", "以后默认给结论。", "", Map.of())).isEmpty());
    }

    @Test
    void rejectsOneShotOperationalCommandsAsUserPreferences() {
        assertTrue(policy.ruleDrafts(message(
                "请再次只读列出 demo-project 项目当前已授权的资源和工具，重点说明是否有 MySQL、Redis、Prometheus、Elasticsearch，只读即可，不要生成 ChangePackage。"))
                .isEmpty());
        assertTrue(policy.ruleDrafts(message(
                "为已确认故障生成重启服务的可审核 ChangePackage，先别执行。"))
                .isEmpty());
    }

    @Test
    void modelCannotPromoteOneShotRequestIntoLongTermUserMemory() {
        ColdMemoryMessageSnapshot oneShot = message(
                "请再次只读列出 demo-project 项目当前已授权的资源和工具，只读即可，不要生成 ChangePackage。");
        MemoryExtractionDraft forgedPreference = new MemoryExtractionDraft(
                "USER_PREFERENCE",
                "用户偏好只读列出资源，不要生成 ChangePackage",
                BigDecimal.valueOf(0.95D),
                "[\"llm\"]",
                "llm_extractor",
                "USER",
                "用户偏好",
                "只读列资源",
                "模型认为这是偏好");

        assertNull(policy.materialize(oneShot, forgedPreference, "hash", "now"));
    }

    @Test
    void materializesTypedCandidateWithNormalizedDefinitionAndMetadataDefaults() {
        ColdMemoryMessageSnapshot message = message("以后优先给结论。");
        MemoryExtractionDraft draft = new MemoryExtractionDraft(
                " user_preference ",
                " 只给必要结论 ",
                BigDecimal.valueOf(2D),
                "[\"llm\"]",
                "llm_extractor",
                "invalid",
                " 偏好 ",
                " 摘要 ",
                " 原因 ");

        MemoryItemCandidate candidate = policy.materialize(message, draft, "hash-1", "2026-07-21 22:00:00");

        assertEquals("USER_PREFERENCE", candidate.memoryType());
        assertEquals("只给必要结论", candidate.content());
        assertEquals(BigDecimal.ONE, candidate.importance());
        assertEquals("USER", candidate.metadata().get("scopeType"));
        assertEquals("demo-project", candidate.metadata().get("projectId"));
        assertEquals("ACTIVE", candidate.metadata().get("memory_status"));
        assertEquals("hash-1", candidate.sourceMessageHash());
        assertTrue(((Integer) candidate.metadata().get("tokenEstimate")) > 0);
    }

    @Test
    void rejectsInvalidOrUnsafeModelDraftsAndDefaultsImportance() {
        ColdMemoryMessageSnapshot message = message("以后优先给结论。");
        assertNull(policy.materialize(message, new MemoryExtractionDraft(
                "UNKNOWN", "内容", null, "[]", "llm", "", "", "", ""), "hash", "now"));
        assertNull(policy.materialize(message, new MemoryExtractionDraft(
                "USER_PREFERENCE", "记住 traceId=abc", null, "[]", "llm", "", "", "", ""),
                "hash", "now"));
        MemoryItemCandidate defaulted = policy.materialize(message, new MemoryExtractionDraft(
                "USER_PREFERENCE", "稳定偏好", null, null, null, null, null, null, null),
                "hash", "now");
        assertEquals(BigDecimal.valueOf(0.72D), defaulted.importance());
        assertEquals("[]", defaulted.tagsJson());
        assertEquals("rule_extractor", defaulted.metadata().get("source"));
    }

    @Test
    void distinctAndLimitPreservesFirstOccurrenceAndFiltersUnsafeCandidates() {
        ColdMemoryMessageSnapshot message = message("以后优先给结论。");
        MemoryItemCandidate first = candidate(message, "USER_PREFERENCE", "偏好一", "hash-1");
        MemoryItemCandidate duplicate = candidate(message, "USER_PREFERENCE", "偏好一", "hash-2");
        MemoryItemCandidate unsafe = new MemoryItemCandidate(
                "s1", "u1", "USER_PREFERENCE", "traceId=abc", BigDecimal.valueOf(0.7D),
                "[]", "user", "hash-3", Map.of(), "now");
        MemoryItemCandidate second = candidate(message, "PROJECT_CONTEXT", "项目背景", "hash-4");
        List<MemoryItemCandidate> input = new ArrayList<>();
        input.add(first);
        input.add(duplicate);
        input.add(unsafe);
        input.add(second);

        List<MemoryItemCandidate> result = policy.distinctAndLimit(input, 2);

        assertEquals(List.of(first, second), result);
        assertFalse(result.contains(duplicate));
        assertEquals(List.of(first), policy.distinctAndLimit(input, 0));
    }

    private MemoryItemCandidate candidate(ColdMemoryMessageSnapshot message,
                                          String type,
                                          String content,
                                          String sourceHash) {
        return policy.materialize(message, new MemoryExtractionDraft(
                type, content, BigDecimal.valueOf(0.7D), "[]", "rule", "", "", "", ""),
                sourceHash, "now");
    }

    private ColdMemoryMessageSnapshot message(String content) {
        return new ColdMemoryMessageSnapshot(
                "s1",
                "u1",
                "user",
                content,
                "2026-07-21 22:00:00",
                Map.of(
                        "agentId", "ops",
                        "projectId", "demo-project",
                        "turn_index", 3L));
    }
}
