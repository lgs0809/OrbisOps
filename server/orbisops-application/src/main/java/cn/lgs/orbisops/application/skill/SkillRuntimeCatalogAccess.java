package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import java.util.*;

/** Recomputed from current governance, project grants and shadowing before recall and use. */
public final class SkillRuntimeCatalogAccess {
    private final SkillCatalogPort catalog;
    public SkillRuntimeCatalogAccess(SkillCatalogPort catalog) { this.catalog=Objects.requireNonNull(catalog); }

    public List<SkillRuntimeCandidate> active(String project) {
        if(project==null || project.isBlank()) throw new IllegalArgumentException("SKILL_PROJECT_ID_REQUIRED");
        var local=catalog.listRuntimeProjectEntries(project);
        var global=catalog.listRuntimeGlobalEntries();
        if(local.size()+global.size()>20_000) throw new IllegalStateException("SKILL_RUNTIME_CATALOG_LIMIT_EXCEEDED");
        Set<String> shadowed=new HashSet<>(), granted=new HashSet<>(catalog.configuredGlobalSkillIds(project));
        Map<String,SkillRuntimeCandidate> result=new LinkedHashMap<>();
        for(var item:local) {
            var c=item.runtimeCandidate();
            if(!"PROJECT".equals(c.scope()) || !project.equals(c.projectId()))
                throw new SecurityException("SKILL_RUNTIME_CATALOG_SCOPE_MISMATCH");
            shadowed.add(c.skillId());
            if(c.activeAtUse() && c.routingReady()) result.put(c.skillId(),c);
        }
        for(var item:global) {
            var c=item.runtimeCandidate();
            if(!"GLOBAL".equals(c.scope()) || !c.projectId().isBlank())
                throw new SecurityException("SKILL_RUNTIME_CATALOG_SCOPE_MISMATCH");
            if(!shadowed.contains(c.skillId()) && granted.contains(c.skillId()) && c.activeAtUse() && c.routingReady())
                result.putIfAbsent(c.skillId(),c);
        }
        return List.copyOf(result.values());
    }

    public void require(String project,String id,String scope) {
        if(active(project).stream().noneMatch(c->c.skillId().equals(id) && c.scope().equals(scope)))
            throw new SkillRuntimeAccessRevokedException();
    }

    public List<SkillRuntimeCandidate> retain(String project,List<SkillRuntimeCandidate> frozen,boolean exactVersion) {
        var current=new HashMap<String,SkillRuntimeCandidate>();active(project).forEach(c->current.put(c.skillId(),c));
        return frozen.stream().filter(c->{
            var now=current.get(c.skillId());
            return now!=null && c.activeAtUse() && now.scope().equals(c.scope()) && now.projectId().equals(c.projectId())
                    && (!exactVersion || (now.version()==c.version() && now.skillHash().equals(c.skillHash()) && now.packageHash().equals(c.packageHash())));
        }).toList();
    }
}
