package cn.lgs.orbisops.domain.memory.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryContentHashPolicyTest {

    private final MemoryContentHashPolicy policy = new MemoryContentHashPolicy();

    @Test
    void preservesLegacyMd5Compatibility() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", policy.stableHash("hello"));
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", policy.stableHash(""));
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", policy.stableHash(null));
    }

    @Test
    void hashesUtf8ContentDeterministically() {
        assertEquals(policy.stableHash("运维记忆"), policy.stableHash("运维记忆"));
        assertEquals(32, policy.stableHash("运维记忆").length());
    }
}
