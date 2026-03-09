package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpsSkillEmbeddingCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntryAtBoundedCapacity() {
        OpsSkillEmbeddingCache cache = new OpsSkillEmbeddingCache(128);
        for (int index = 0; index < 128; index++) {
            cache.put("key-" + index, new float[]{index});
        }
        cache.get("key-0");
        cache.put("key-128", new float[]{128F});

        assertEquals(128, cache.size());
        assertNull(cache.get("key-1"));
        assertEquals(0F, cache.get("key-0")[0]);
    }
}
