package cn.lgs.orbisops.domain.skill.service;

import java.util.*;

/** Published source identities stay consumed across retries, revisions and late event correlations. */
public final class SkillNovelSourcePolicy {
    public record Source(String episodeId, String conditionKey, Set<String> associations) {
        public Source {
            if (episodeId == null || episodeId.isBlank()) throw new IllegalArgumentException("SKILL_SOURCE_ID_REQUIRED");
            conditionKey = conditionKey == null ? "" : conditionKey;
            associations = associations == null ? Set.of() : Set.copyOf(associations);
        }
    }

    public List<Source> novel(List<Source> proposed, List<Source> consumed) {
        Map<String,String> parents = new HashMap<>();
        for (Source source : java.util.stream.Stream.concat(proposed.stream(), consumed.stream()).toList()) {
            String task = "episode:" + source.episodeId();
            root(parents, task);
            for (String alias : source.associations()) {
                if (alias.isBlank()) throw new IllegalArgumentException("SKILL_SOURCE_ASSOCIATION_REQUIRED");
                String left = root(parents, task), right = root(parents, alias);
                if (!left.equals(right)) parents.put(right, left);
            }
        }
        Set<String> old = new HashSet<>();
        consumed.forEach(s -> old.add(root(parents, "episode:" + s.episodeId())));
        Map<String,Source> independent = new LinkedHashMap<>();
        for (Source source : proposed) {
            String id = root(parents, "episode:" + source.episodeId());
            if (!old.contains(id)) independent.putIfAbsent(id, source);
        }
        return List.copyOf(independent.values());
    }

    public void requirePatchSources(List<Source> proposed, List<Source> consumed) {
        List<Source> fresh = novel(proposed, consumed);
        if (fresh.size() < 3) throw new IllegalStateException("SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES");
        if (fresh.stream().map(Source::conditionKey).filter(s -> !s.isBlank()).distinct().count() < 2)
            throw new IllegalStateException("SKILL_EVOLUTION_INSUFFICIENT_NEW_CONDITIONS");
    }

    private String root(Map<String,String> parents, String id) {
        parents.putIfAbsent(id, id);
        String root = id;
        while (!parents.get(root).equals(root)) root = parents.get(root);
        while (!id.equals(root)) { String next = parents.put(id, root); id = next; }
        return root;
    }
}
