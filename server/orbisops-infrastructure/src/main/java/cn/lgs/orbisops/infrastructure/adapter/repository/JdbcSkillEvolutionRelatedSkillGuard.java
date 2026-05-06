package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.application.skill.SkillEvolutionProposalSnapshot;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Shares the proposal transaction; catalog locks precede the candidate asset reservation. */
final class JdbcSkillEvolutionRelatedSkillGuard {
    private final JdbcTemplate jdbc;
    JdbcSkillEvolutionRelatedSkillGuard(JdbcTemplate jdbc) {this.jdbc=jdbc;}

    void lockAndVerify(String projectId,Map<String,Object> input) {
        if(!VERSION.equals(input.get("relatedSkillPolicyVersion"))) throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_SET_INVALID");
        var catalog=new JdbcSkillCatalogRepository(jdbc);
        for(var skill:references(input.get("relatedSkills"),projectId).stream()
                .sorted(Comparator.comparing(s->text(s.get("scope"))+":"+text(s.get("skillId")))).toList()) {
            String scope=text(skill.get("scope")),project=text(skill.get("projectId")),id=text(skill.get("skillId"));
            var current=catalog.lockEvolutionEntry(scope,project,id);
            if("FILE".equals(skill.get("sourceType"))) {
                if(current.isPresent()) changed(); // A DB override cannot replace the frozen file reference.
                continue; // File bytes are revalidated by the application against its deployed SDK catalog.
            }
            if(current.isEmpty()) changed();
            var entry=current.orElseThrow();
            if(new JdbcSkillLifecycleVisibility(jdbc).filter(projectId,List.of(entry),
                    cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry::scope,
                    cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry::skillId,Set.of()).isEmpty()) changed();
            if(!databaseFence(entry).equals(skill.get("catalogFence"))
                    || entry.currentVersion()!=number(skill.get("currentVersion"))
                    || !entry.currentSkillHash().equals(skill.get("currentSkillHash"))
                    || !entry.content().equals(skill.get("content"))) changed();
            verifyResources(scope,project,id,entry.currentVersion(),skill);
        }
    }

    void requireTarget(SkillEvolutionProposalSnapshot plan,SkillPatchCandidate candidate) {
        if(candidate.targetSkillId().isBlank()) {
            if(candidate.baseSkillVersion()!=0 || !candidate.baseSkillHash().isBlank()) changed();
            return;
        }
        var selected=references(plan.input().get("relatedSkills"),candidate.projectId()).stream()
                .filter(s->"PROJECT".equals(s.get("scope"))&&candidate.targetSkillId().equals(s.get("skillId"))).toList();
        if(selected.size()!=1) throw new IllegalStateException("SKILL_EVOLUTION_TARGET_OUTSIDE_FROZEN_SET");
        var skill=selected.get(0);
        if(!"DB".equals(skill.get("sourceType")) || number(skill.get("currentVersion"))!=candidate.baseSkillVersion()
                || !candidate.baseSkillHash().equals(skill.get("currentSkillHash"))) changed();
        var entry=new JdbcSkillCatalogRepository(jdbc).lockEvolutionEntry("PROJECT",candidate.projectId(),candidate.targetSkillId()).orElseThrow();
        if(!entry.autoPublishSkipReason().isBlank()) throw new IllegalStateException("SKILL_EVOLUTION_TARGET_NOT_AUTOMATIC");
    }

    private void verifyResources(String scope,String project,String id,int version,Map<String,Object> skill) {
        if(!(skill.get("relatedArtifacts") instanceof List<?>)) changed();
        var expected=(List<?>)skill.get("relatedArtifacts");
        if(!(skill.get("artifactHashes") instanceof Map<?,?>)) changed();
        var hashes=(Map<?,?>)skill.get("artifactHashes");
        if(hashes.size()!=expected.size()+1
                || !CanonicalObjectHasher.sha256Text(String.valueOf(skill.get("content"))).equals(hashes.get("SKILL.md"))
                || !CanonicalObjectHasher.sha256(Map.of("manifestHash",CanonicalObjectHasher.sha256Text(String.valueOf(skill.get("packageManifestJson"))),
                        "artifactHashes",hashes)).equals(skill.get("currentPackageHash"))) changed();
        var all=jdbc.queryForList("SELECT artifact_path,content,content_hash,content_encoding FROM ai_ops_skill_artifact WHERE scope=? AND project_id=? AND skill_id=? AND version=? ORDER BY artifact_path FOR UPDATE",
                scope,project,id,version);
        for(var row:all) if("SKILL.md".equals(row.get("artifact_path"))) {
            if(!Objects.equals(row.get("content"),skill.get("content"))
                    || !artifactHash(row).equals(row.get("content_hash"))) changed();
        }
        var rows=all.stream().filter(r->!"SKILL.md".equals(r.get("artifact_path"))).toList();
        if(rows.size()!=expected.size()) changed();
        for(var row:rows) {
            var matches=expected.stream().filter(v->v instanceof Map<?,?> m&&row.get("artifact_path").equals(m.get("path"))).toList();
            if(matches.size()!=1) changed(); var frozen=(Map<?,?>)matches.get(0);
            String content=String.valueOf(row.get("content"));
            if(!content.equals(frozen.get("content")) || !row.get("content_hash").equals(frozen.get("contentHash"))
                    || !row.get("content_hash").equals(hashes.get(row.get("artifact_path")))
                    || !Objects.equals(row.get("content_encoding"),frozen.get("encoding"))
                    || !artifactHash(row).equals(row.get("content_hash"))) changed();
        }
    }
    private String artifactHash(Map<String,Object> row) {
        String content=String.valueOf(row.get("content"));
        if("UTF8".equals(row.get("content_encoding"))) return CanonicalObjectHasher.sha256Text(content);
        if(!"BASE64".equals(row.get("content_encoding"))) {changed();return "";}
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(content)));}
        catch(Exception invalid) {throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",invalid);}
    }
    private void changed() {throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED");}
}
