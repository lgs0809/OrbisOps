package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Adds published-method provenance to the same frozen proposal, without creating or claiming jobs. */
final class JdbcSkillEvolutionSourcePortfolio {
    static final String VERSION="published-method-source-portfolio-v1";
    private final JdbcTemplate jdbc;
    JdbcSkillEvolutionSourcePortfolio(JdbcTemplate jdbc) {this.jdbc=jdbc;}

    Map<String,Object> enrich(String project,Map<String,Object> input) {
        var primary=samples(input);
        var all=new LinkedHashMap<String,Map<String,Object>>();
        primary.forEach(s->{if(all.put(text(s.get("sourceId")),s)!=null) invalid();});
        var primaryIds=List.copyOf(all.keySet());
        var groups=new ArrayList<Map<String,Object>>();
        for(var skill:references(project,input)) {
            var published=publishedSources(project,skill);
            groups.add(Map.of("skillId",text(skill.get("skillId")),"sourceIds",List.copyOf(published.keySet())));
            published.forEach(all::putIfAbsent);
        }
        // Do not silently omit historic conditions to manufacture a split/merge proposal.
        if(all.size()>cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy.ARCHIVE_LIMIT)
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_PORTFOLIO_BUDGET");
        var independent=new JdbcVerifiedSkillSourceReader(jdbc).sourcesByIds(project,List.copyOf(all.keySet()));
        var liveIds=new HashSet<String>();independent.forEach(s->liveIds.add(text(s.get("acceptance_id"))));
        if(!liveIds.containsAll(primaryIds)) invalid();
        var full=new JdbcSkillEvolutionSourceReader(jdbc);
        var selected=new ArrayList<Map<String,Object>>();
        for(var source:all.values()) if(liveIds.contains(text(source.get("sourceId")))) {
            var current=full.loadAccepted(project,text(source.get("runId")),text(source.get("sourceId")));
            if(!current.sourceHash().equals(source.get("sourceHash"))) invalid();
            selected.add(source);
        }
        var result=new LinkedHashMap<>(input);
        result.put("sourcePortfolioPolicyVersion",VERSION);
        result.put("primarySourceIds",primaryIds);
        result.put("consolidatedExperiences",List.copyOf(selected));
        result.put("relatedSkillSourceGroups",groups.stream().map(g->Map.of("skillId",g.get("skillId"),
                "sourceIds",strings(g.get("sourceIds")).stream().filter(liveIds::contains).toList())).toList());
        result.put("excludedRelatedSourceIds",all.keySet().stream().filter(id->!liveIds.contains(id)).toList());
        return result;
    }

    /** Publication rechecks provenance and cross-group incident identity under the proposal's source locks. */
    void requireCurrent(String project,Map<String,Object> input) {
        if(!VERSION.equals(input.get("sourcePortfolioPolicyVersion"))) invalid();
        var all=samples(input);var ids=all.stream().map(s->text(s.get("sourceId"))).toList();
        var primary=primaryIds(input);
        if(!new HashSet<>(ids).containsAll(primary)) invalid();
        var live=new JdbcVerifiedSkillSourceReader(jdbc).sourcesByIds(project,ids).stream()
                .map(s->text(s.get("acceptance_id"))).collect(java.util.stream.Collectors.toSet());
        if(!live.equals(new HashSet<>(ids))) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID");
        var allowed=new HashSet<>(primary);var seen=new HashSet<String>();
        var references=references(project,input);
        for(var group:maps(input.get("relatedSkillSourceGroups"))) {
            String id=text(group.get("skillId"));if(!seen.add(id)) invalid();
            var skill=references.stream().filter(s->id.equals(s.get("skillId"))).findFirst().orElseThrow(JdbcSkillEvolutionSourcePortfolio::failure);
            var history=publishedSources(project,skill);var grouped=strings(group.get("sourceIds"));
            if(!history.keySet().containsAll(grouped) || !live.containsAll(grouped)) invalid();
            for(var source:all) if(grouped.contains(text(source.get("sourceId")))) {
                if(!CanonicalJson.stringify(source).equals(CanonicalJson.stringify(history.get(text(source.get("sourceId")))))
                        && !primary.contains(text(source.get("sourceId")))) invalid();
            }
            allowed.addAll(grouped);
        }
        if(!seen.equals(references.stream().map(s->text(s.get("skillId"))).collect(java.util.stream.Collectors.toSet())) || !allowed.containsAll(ids)) invalid();
    }

    Map<String,Map<String,Object>> publishedSources(String project,Map<String,Object> skill) {
        return publishedSources(project,skill,false);
    }

    Map<String,Map<String,Object>> publishedSources(String project,Map<String,Object> skill,boolean allVersions) {
        var result=new LinkedHashMap<String,Map<String,Object>>();
        if(!"PROJECT".equals(skill.get("scope")) || !"DB".equals(skill.get("sourceType"))) return result;
        String id=text(skill.get("skillId"));
        String query="SELECT version,source_type,evolution_job_id,skill_hash,package_hash FROM ai_ops_skill_version WHERE scope='PROJECT' AND project_id=? AND skill_id=?";
        var args=new ArrayList<Object>(List.of(project,id));
        if(!allVersions) {query+=" AND version<=?";args.add(number(skill.get("currentVersion")));}
        var versions=jdbc.queryForList(query+" ORDER BY version LIMIT 1001",args.toArray());
        if(versions.size()>1000) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_HISTORY_LIMIT");
        int bytes=0;
        for(var version:versions) {
            String type=text(version.get("source_type")).toUpperCase(Locale.ROOT),reference=text(version.get("evolution_job_id"));
            if(!type.contains("EVOL") && reference.isBlank()) continue;
            if(reference.isBlank()) invalid();
            if(number(version.get("version"))==number(skill.get("currentVersion"))
                    && (!Objects.equals(version.get("skill_hash"),skill.get("currentSkillHash"))
                    || !Objects.equals(version.get("package_hash"),skill.get("currentPackageHash")))) invalid();
            var plans=jdbc.queryForList("""
                    SELECT candidate_id,input_json,plan_hash FROM ai_ops_skill_evolution_proposal
                    WHERE project_id=? AND (candidate_id=? OR job_id=?) LIMIT 2
                    """,project,reference,reference);
            if(plans.size()!=1) invalid();
            var plan=plans.get(0);String raw=text(plan.get("input_json"));bytes+=raw.length();
            if(bytes>16_000_000) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_HISTORY_LIMIT");
            if(!hash(raw).equals(plan.get("plan_hash")) || !CanonicalJson.stringify(object(raw)).equals(raw)) invalid();
            var archived=samples(object(raw));
            // Each atomically published branch owns only its declared source subset.
            var atomic=jdbc.queryForList("SELECT plan_json,plan_hash FROM ai_ops_skill_atomic_publication WHERE candidate_id=? AND project_id=?",plan.get("candidate_id"),project);
            Set<String> branchIds=null;
            if(!atomic.isEmpty()) {
                String frozen=text(atomic.get(0).get("plan_json"));if(!hash(frozen).equals(atomic.get(0).get("plan_hash"))) invalid();
                var matches=maps(object(frozen).get("targets")).stream().filter(t->id.equals(t.get("skillId"))).toList();
                if(matches.size()!=1) invalid();branchIds=new HashSet<>(strings(matches.get(0).get("sourceIds")));
            }
            var selected=branchIds==null?primaryIds(object(raw)):branchIds;
            for(var source:archived) if(selected.contains(text(source.get("sourceId")))) {
                var whole=object(source.get("acceptedTaskEpisode"));
                if(!project.equals(whole.get("projectId")) || !text(source.get("sourceHash")).equals(hash(text(source.get("acceptedTaskEpisode"))))) invalid();
                var previous=result.putIfAbsent(text(source.get("sourceId")),source);
                if(previous!=null && !CanonicalJson.stringify(previous).equals(CanonicalJson.stringify(source))) invalid();
            }
            if(!archived.stream().map(s->text(s.get("sourceId"))).collect(java.util.stream.Collectors.toSet()).containsAll(selected)) invalid();
        }
        if("EVOLVED".equals(skill.get("origin")) && result.isEmpty()) invalid();
        return result;
    }

    static Set<String> primaryIds(Map<String,Object> input) {
        var all=samples(input).stream().map(s->text(s.get("sourceId"))).collect(java.util.stream.Collectors.toSet());
        if(!input.containsKey("sourcePortfolioPolicyVersion")) return all; // Immutable legacy proposals remain readable.
        if(!VERSION.equals(input.get("sourcePortfolioPolicyVersion"))) invalid();
        var ids=strings(input.get("primarySourceIds"));
        if(ids.size()<3 || !all.containsAll(ids) || new HashSet<>(ids).size()!=ids.size()) invalid();
        return Set.copyOf(ids);
    }
    private List<Map<String,Object>> references(String project,Map<String,Object> input) {
        return SkillEvolutionRelatedSkillPolicy.references(input.get("relatedSkills"),project).stream()
                .filter(s->"PROJECT".equals(s.get("scope")) && "DB".equals(s.get("sourceType"))).toList();
    }
    private static List<Map<String,Object>> samples(Map<String,Object> input) {return maps(input.get("consolidatedExperiences"));}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> maps(Object value) {
        if(!(value instanceof List<?> list) || list.stream().anyMatch(v->!(v instanceof Map<?,?>))) throw failure();
        return (List<Map<String,Object>>)value;
    }
    private static List<String> strings(Object value) {
        if(!(value instanceof Collection<?> list) || list.stream().anyMatch(v->!(v instanceof String s)||s.isBlank())) throw failure();
        return list.stream().map(v->(String)v).toList();
    }
    private static void invalid() {throw failure();}
    private static IllegalStateException failure() {return new IllegalStateException("SKILL_EVOLUTION_PUBLISHED_SOURCE_HISTORY_REQUIRED");}
}
