package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillEvolutionProposalSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillNovelSourcePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Uses immutable published-version provenance, never rejected/pending proposals, as consumed evidence. */
final class JdbcSkillNovelSourceGuard {
    private final JdbcTemplate jdbc;
    JdbcSkillNovelSourceGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void requireNovel(SkillEvolutionProposalSnapshot plan, SkillPatchCandidate candidate) {
        if (candidate.targetSkillId().isBlank()) return;
        String project = candidate.projectId();
        var proposed = samples(plan.input(), project);
        var references=cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy.references(plan.input().get("relatedSkills"),project);
        var selected=references.stream().filter(s->"PROJECT".equals(s.get("scope")) && candidate.targetSkillId().equals(s.get("skillId"))).toList();
        if(selected.size()!=1) throw historyMissing();
        // Rollback does not make sources consumed by a later published version new again.
        var history=new JdbcSkillEvolutionSourcePortfolio(jdbc).publishedSources(project,selected.get(0),true);
        // The same reader also narrows atomic publications to this branch's actual sources.
        var consumed=history.values().stream().map(s->identity(s,project)).toList();
        String origin = jdbc.queryForObject("SELECT origin FROM ai_ops_skill WHERE scope='PROJECT' AND project_id=? AND skill_id=?",
                String.class, project, candidate.targetSkillId());
        if ("EVOLVED".equals(origin) && history.isEmpty()) throw historyMissing();
        Map<String,Set<String>> aliases = associations(project, proposed, consumed);
        new SkillNovelSourcePolicy().requirePatchSources(identities(proposed, aliases), identities(consumed, aliases));
    }

    private List<Map<String,Object>> samples(Map<String,Object> input, String project) {
        if (!(input.get("consolidatedExperiences") instanceof List<?> list) || list.isEmpty()
                || list.size() > cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy.ARCHIVE_LIMIT) throw historyMissing();
        List<Map<String,Object>> result = new ArrayList<>();
        var primary=JdbcSkillEvolutionSourcePortfolio.primaryIds(input);
        for (Object item : list) {
            if (!(item instanceof Map<?,?> source)) throw historyMissing();
            if(!primary.contains(text(source.get("sourceId")))) continue;
            result.add(identity(source,project));
        }
        return result;
    }

    private Map<String,Object> identity(Map<?,?> source,String project) {
        var full=object(source.get("acceptedTaskEpisode"));
        if(!project.equals(full.get("projectId")) || !"accepted-task-episode-v1".equals(full.get("format"))
                || !text(source.get("taskEpisodeId")).equals(full.get("episodeId"))
                || !text(source.get("sourceHash")).equals(hash(text(source.get("acceptedTaskEpisode"))))) throw historyMissing();
        return Map.of("episodeId",text(source.get("taskEpisodeId")),"conditionKey",text(source.get("conditionKey")));
    }

    private Map<String,Set<String>> associations(String project, List<Map<String,Object>> proposed, List<Map<String,Object>> consumed) {
        var ids = java.util.stream.Stream.concat(proposed.stream(), consumed.stream())
                .map(s -> text(s.get("episodeId"))).distinct().sorted().toList();
        if (ids.size() > 2000) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_HISTORY_LIMIT");
        Map<String,Set<String>> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            var batch = ids.subList(start, Math.min(start + 100, ids.size()));
            List<Object> args = new ArrayList<>(); args.add(project); args.addAll(batch);
            var rows = jdbc.queryForList("""
                    SELECT DISTINCT t.episode_id,i.incident_id,g.group_id FROM ai_ops_task_episode_turn t
                    JOIN ai_ops_incident_run r ON r.run_id=t.source_run_ref
                    JOIN ai_ops_incident i ON i.incident_id=r.incident_id AND i.project_id=t.project_id
                    LEFT JOIN ai_ops_alert_correlation_member m ON m.incident_id=i.incident_id
                    LEFT JOIN ai_ops_alert_correlation_group g ON g.group_id=m.group_id AND g.project_id=t.project_id AND g.merged_into IS NULL
                    WHERE t.project_id=? AND t.episode_id IN (
                    """ + String.join(",", Collections.nCopies(batch.size(), "?")) + ") LIMIT 10001", args.toArray());
            if (rows.size() > 10000) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_HISTORY_LIMIT");
            for (var row : rows) for (String field : List.of("incident_id", "group_id")) {
                String value = text(row.get(field));
                if (!value.isBlank()) result.computeIfAbsent(text(row.get("episode_id")), k -> new HashSet<>()).add(field + ":" + value);
            }
        }
        return result;
    }

    private List<SkillNovelSourcePolicy.Source> identities(List<Map<String,Object>> sources, Map<String,Set<String>> aliases) {
        return sources.stream().map(s -> new SkillNovelSourcePolicy.Source(text(s.get("episodeId")),text(s.get("conditionKey")),
                aliases.getOrDefault(text(s.get("episodeId")),Set.of()))).toList();
    }
    private IllegalStateException historyMissing() { return new IllegalStateException("SKILL_EVOLUTION_PUBLISHED_SOURCE_HISTORY_REQUIRED"); }
}
