package cn.lgs.orbisops.domain.memory.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextMemoryDefinitionPolicyTest {

    private final ContextMemoryDefinitionPolicy policy = new ContextMemoryDefinitionPolicy();

    @Test
    void normalizesAllowedScopeTypeAndStatusValues() {
        assertEquals("PROJECT", policy.normalizeScope(" project ", true));
        assertEquals("USER_PREFERENCE", policy.normalizeMemoryType(" user_preference ", true));
        assertEquals("ACTIVE", policy.normalizeStatus(" active ", true));
        assertEquals("", policy.normalizeScope("unknown", false));
        assertEquals("", policy.normalizeMemoryType("unknown", false));
        assertEquals("", policy.normalizeStatus("unknown", false));
    }

    @Test
    void rejectsRequiredInvalidValuesAndScopeTypeMismatch() {
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeScope("", true));
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeMemoryType("UNKNOWN", true));
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeStatus("DELETED", true));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateScopeAndType("USER", "PROJECT_CONTEXT"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateScopeAndType("PROJECT", "USER_PREFERENCE"));
    }

    @Test
    void clampsConfidenceAndPreservesDefault() {
        assertEquals(BigDecimal.ZERO, policy.normalizeConfidence(BigDecimal.valueOf(-1D)));
        assertEquals(BigDecimal.ONE, policy.normalizeConfidence(BigDecimal.valueOf(2D)));
        assertEquals(BigDecimal.valueOf(0.6D), policy.normalizeConfidence(BigDecimal.valueOf(0.6D)));
        assertEquals(BigDecimal.valueOf(0.8D), policy.normalizeConfidence(null));
    }

    @Test
    void selectsMemoryTypesForRuntimeScenesInStablePriority() {
        assertEquals(
                List.of("PROJECT_GLOSSARY", "PROJECT_CONVENTION", "USER_PREFERENCE"),
                policy.memoryTypesForScene("OPS_TROUBLESHOOTING"));
        assertEquals(
                List.of("PROJECT_CONTEXT", "PROJECT_CONVENTION", "USER_WORKFLOW", "USER_DOMAIN_FOCUS"),
                policy.memoryTypesForScene("DESIGN_DISCUSSION"));
        assertEquals(
                List.of("PROJECT_CONTEXT", "PROJECT_CONVENTION", "PROJECT_GLOSSARY", "USER_PREFERENCE"),
                policy.memoryTypesForScene("DOCUMENT_WRITING"));
        assertEquals(
                List.of("PROJECT_CONTEXT", "PROJECT_CONVENTION", "PROJECT_GLOSSARY"),
                policy.memoryTypesForScene("SKILL_EVOLVER"));
        assertEquals(
                List.of("USER_PREFERENCE", "PROJECT_CONTEXT"),
                policy.memoryTypesForScene("CHAT"));
    }
}
