package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.util.*;
import static cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy.*;

/** Metadata ranking precedes full package reads. The author and matcher share exactly these versions. */
public final class SkillEvolutionRelatedSkillService {
    private final ISkillCatalogRepository repository;
    private final SkillFileSourcePort files;
    private final SkillCatalogQueryService catalog;

    public SkillEvolutionRelatedSkillService(ISkillCatalogRepository repository, SkillFileSourcePort files,
                                             SkillCatalogQueryService catalog) {
        this.repository=Objects.requireNonNull(repository); this.files=Objects.requireNonNull(files);
        this.catalog=Objects.requireNonNull(catalog);
    }

    public List<Map<String,Object>> select(String projectId, Map<String,Object> input) {
        if(projectId==null || projectId.isBlank()) throw new IllegalArgumentException("SKILL_PROJECT_ID_REQUIRED");
        var query=terms(text(input.get("normalizedUserGoal"))+" "+text(input.get("finalOutput"))+" "+text(input.get("toolEvidence")));
        var metadata=metadata(projectId);
        var ranked=metadata.values().stream().map(s->new Ranked(s,score(query,terms(searchText(s)))))
                .filter(s->s.score()>0).sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(s->key(s.skill())))
                .limit(MAX_RELATED_SKILLS).toList();
        var full=new ArrayList<Map<String,Object>>();
        for(var selected:ranked) {
            var summary=selected.skill(); String id=text(summary.get("skillId"));
            var skill=new LinkedHashMap<>("GLOBAL".equals(summary.get("scope"))
                    ?catalog.getGlobalSkill(id):catalog.getProjectSkill(projectId,id));
            if(!summary.get("sourceType").equals(skill.get("sourceType"))) changed();
            // DB rows are checked again under locks by the durable proposal store; this fence also covers legacy rows.
            skill.put("catalogFence",summary.get("catalogFence"));
            skill.put("relatedMetadataScore",selected.score());
            var artifacts=catalog.listSkillArtifacts(text(skill.get("projectId")),id,
                    ((Number)skill.get("currentVersion")).intValue(),text(skill.get("currentSkillHash")),
                    text(skill.get("currentPackageHash")),text(skill.get("scope")));
            // The entry point already appears as content. Resources are retained in full, with their hashes.
            skill.put("relatedArtifacts",artifacts.stream().filter(a->!"SKILL.md".equals(a.get("path"))).toList());
            skill.remove("markdown"); skill.remove("xml"); skill.remove("basePath");
            full.add(skill);
        }
        return references(full,projectId);
    }

    public void requireCurrent(String projectId,List<Map<String,Object>> frozen) {
        var metadata=metadata(projectId);
        for(var skill:references(frozen,projectId)) {
            var current=metadata.get(key(skill));
            if(current==null || !current.get("sourceType").equals(skill.get("sourceType"))
                    || !current.get("catalogFence").equals(skill.get("catalogFence"))) changed();
            if("FILE".equals(skill.get("sourceType"))) {
                var file=files.findById(text(skill.get("skillId"))).orElseThrow(()->new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED"));
                var view=new SkillFileCatalogViewMapper().toView(file,false);
                if(!view.get("currentSkillHash").equals(skill.get("currentSkillHash"))
                        || !view.get("currentPackageHash").equals(skill.get("currentPackageHash"))) changed();
            }
        }
    }

    private Map<String,Map<String,Object>> metadata(String projectId) {
        if(!repository.available()) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
        var result=new TreeMap<String,Map<String,Object>>();
        for(String scope:List.of("GLOBAL","PROJECT")) {
            for(var entry:repository.findAuthoringMetadata(scope,"GLOBAL".equals(scope)?"":projectId)) {
                var row=new LinkedHashMap<String,Object>();
                row.put("skillId",entry.skillId());row.put("scope",entry.scope());row.put("projectId",entry.projectId());
                row.put("name",entry.name());row.put("description",entry.description());
                row.put("routingProfile",cn.lgs.orbisops.domain.skill.service.SkillPackageManifest.routingProfile(entry.packageManifestJson()));
                row.put("sourceType","DB");row.put("catalogFence",databaseFence(entry));
                result.put(key(row),row);
            }
        }
        // The SDK caches file definitions. Only metadata participates in ranking; no body or manifest is hashed here.
        for(var file:files.findAll()) {
            var fm=file.frontMatter();String scope=text(fm.getOrDefault("scope","GLOBAL")).toUpperCase(Locale.ROOT);
            String project=text(fm.getOrDefault("projectId",fm.get("project_id")));
            if(!("GLOBAL".equals(scope)&&project.isBlank() || "PROJECT".equals(scope)&&projectId.equals(project))) continue;
            var row=new LinkedHashMap<String,Object>();
            row.put("skillId",file.name());row.put("scope",scope);row.put("projectId",project);row.put("name",file.name());
            row.put("description",text(fm.get("description")));row.put("routingProfile",fm.getOrDefault("routingProfile",Map.of()));
            row.put("sourceType","FILE");row.put("catalogFence",CanonicalObjectHasher.sha256(row));
            result.putIfAbsent(key(row),row);
        }
        return result;
    }

    private String searchText(Map<String,Object> skill) {
        return text(skill.get("name"))+" "+text(skill.get("description"))+" "+text(skill.get("routingProfile"));
    }
    private Set<String> terms(String value) {
        String normalized=value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip();
        var result=new HashSet<String>();
        for(String word:normalized.split(" +")) {
            int[] points=word.codePoints().toArray(); if(points.length<2) continue;
            result.add(word);
            for(int i=0;i<points.length-1;i++) result.add(new String(points,i,2));
        }
        return result;
    }
    private double score(Set<String> left,Set<String> right) {
        if(left.isEmpty()||right.isEmpty()) return 0;
        long overlap=right.stream().filter(left::contains).count();
        return overlap/Math.sqrt((double)left.size()*right.size());
    }
    private String key(Map<String,Object> skill) { return text(skill.get("scope"))+":"+text(skill.get("skillId")); }
    private String text(Object value) {return value==null?"":String.valueOf(value).trim();}
    private void changed() { throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED"); }
    private record Ranked(Map<String,Object> skill,double score) { }
}
