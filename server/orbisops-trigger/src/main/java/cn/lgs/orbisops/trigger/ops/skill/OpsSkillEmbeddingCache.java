package cn.lgs.orbisops.trigger.ops.skill;

import java.util.LinkedHashMap;
import java.util.Map;

/** Synchronized bounded LRU state for reusable Skill document embeddings. */
final class OpsSkillEmbeddingCache {

    private final Map<String, float[]> embeddings;

    OpsSkillEmbeddingCache(int maxSize) {
        int boundedSize = Math.max(128, maxSize);
        this.embeddings = new LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > boundedSize;
            }
        };
    }

    synchronized boolean contains(String key) {
        return embeddings.containsKey(key);
    }

    synchronized float[] get(String key) {
        return embeddings.get(key);
    }

    synchronized void put(String key, float[] vector) {
        if (key != null && vector != null) {
            embeddings.put(key, vector);
        }
    }

    synchronized int size() {
        return embeddings.size();
    }
}
