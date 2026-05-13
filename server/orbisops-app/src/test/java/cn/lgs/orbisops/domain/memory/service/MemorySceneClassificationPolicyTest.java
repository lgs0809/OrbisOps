package cn.lgs.orbisops.domain.memory.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemorySceneClassificationPolicyTest {

    private final MemorySceneClassificationPolicy policy = new MemorySceneClassificationPolicy();

    @Test
    void explicitSceneHasHighestPriority() {
        assertEquals("CUSTOM_SCENE", policy.classify(" CUSTOM_SCENE ", "OPS_TASK", "故障排查"));
    }

    @Test
    void taskTypeIsUsedWhenSceneIsBlank() {
        assertEquals("OPS_TASK", policy.classify(" ", " OPS_TASK ", "故障排查"));
    }

    @Test
    void classifiesTroubleshootingKeywords() {
        assertEquals(MemorySceneClassificationPolicy.OPS_TROUBLESHOOTING,
                policy.classify(null, null, "请分析这条告警和异常原因"));
        assertEquals(MemorySceneClassificationPolicy.OPS_TROUBLESHOOTING,
                policy.classify(null, null, "系统故障排障"));
        assertEquals(MemorySceneClassificationPolicy.OPS_TROUBLESHOOTING,
                policy.classify(null, null, "定位错误"));
    }

    @Test
    void classifiesDocumentAndDesignKeywordsInStablePriority() {
        assertEquals(MemorySceneClassificationPolicy.DOCUMENT_WRITING,
                policy.classify(null, null, "帮我润色设计报告"));
        assertEquals(MemorySceneClassificationPolicy.DESIGN_DISCUSSION,
                policy.classify(null, null, "讨论系统方案和规划"));
    }

    @Test
    void fallsBackToChat() {
        assertEquals(MemorySceneClassificationPolicy.CHAT,
                policy.classify(null, null, "今天进展如何"));
        assertEquals(MemorySceneClassificationPolicy.CHAT,
                policy.classify(null, null, null));
    }
}
