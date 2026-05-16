package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/** File ownership is a CAS provenance pointer; a user edit/governance change relinquishes file ownership. */
@Repository
public class JdbcSkillFileProjectionRepository implements SkillFileProjectionPort {
    private final JdbcTemplate jdbc;
    private final JdbcSkillCatalogRepository catalog;
    private final ISkillPackageRepository packages;
    private final TransactionTemplate tx;
    @Autowired
    public JdbcSkillFileProjectionRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc,
            JdbcSkillCatalogRepository catalog, ISkillPackageRepository packages) { this(jdbc.getIfAvailable(),catalog,packages); }
    public JdbcSkillFileProjectionRepository(JdbcTemplate jdbc, JdbcSkillCatalogRepository catalog, ISkillPackageRepository packages) {
        this.jdbc=jdbc;this.catalog=catalog;this.packages=packages;
        tx=jdbc==null?null:new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    @Override public void synchronize(SkillFileDefinition file) {
        if(jdbc==null) throw new IllegalStateException("SKILL_FILE_PROJECTION_STORE_UNAVAILABLE");
        Map<String,Object> metadata=new SkillFileCatalogViewMapper().toView(file,false);
        String scope=(String)metadata.get("scope"), project=(String)metadata.get("projectId"), id=(String)metadata.get("skillId");
        if(!Set.of("GLOBAL","PROJECT").contains(scope) || "GLOBAL".equals(scope)&&!project.isBlank()
                || "PROJECT".equals(scope)&&project.isBlank()) throw new IllegalArgumentException("SKILL_FILE_SCOPE_INVALID");
        String sourceHash=CanonicalObjectHasher.sha256Text(CanonicalJson.stringify(Map.of("name",file.name(),"basePath",file.basePath(),
                "frontMatter",file.frontMatter(),"content",file.content())));
        tx.executeWithoutResult(ignored->{
            jdbc.update("INSERT INTO ai_ops_skill_file_projection(scope,project_id,skill_id) VALUES (?,?,?) ON DUPLICATE KEY UPDATE skill_id=VALUES(skill_id)",scope,project,id);
            var source=jdbc.queryForMap("SELECT source_hash,projected_version,projected_skill_hash FROM ai_ops_skill_file_projection WHERE scope=? AND project_id=? AND skill_id=? FOR UPDATE",scope,project,id);
            var head=catalog.lockEvolutionEntry(scope,project,id).orElse(null);
            // A pre-existing manual asset, edit, lock or governance change always wins over the file importer.
            if(head!=null && (head.currentVersion()!=((Number)source.get("projected_version")).intValue()
                    || !head.currentSkillHash().equals(source.get("projected_skill_hash")))) return;
            if(head!=null && sourceHash.equals(source.get("source_hash"))) return;
            int version=head==null?1:head.currentVersion()+1;
            String description=Objects.toString(file.frontMatter().get("description"),"");
            var descriptor=SkillPackageManifest.create(scope,project,id,file.name(),description,version,file.content(),file.frontMatter(),SkillPackageManifest.Limits.defaults());
            String hash=SkillCatalogFingerprint.sha256(scope,project,id,file.name(),description,file.content(),version,
                    head==null?"ENABLED":head.status(),head==null?"MANUAL_ONLY":head.updateMode(),false,false);
            String artifactHashes=CanonicalJson.stringify(descriptor.artifactHashes());
            if(head==null) {
                var entry=new SkillCatalogEntry(0,id,project,file.name(),scope,"",description,file.content(),version,"ENABLED","file-projection",null,null,
                        "IMPORTED","MANUAL_ONLY",false,false,null,"","",null,hash,version,hash,version,descriptor.packageHash(),descriptor.manifestJson(),artifactHashes);
                if(!catalog.insertIfAbsent(entry)) throw new IllegalStateException("SKILL_FILE_PROJECTION_CONCURRENT_HEAD");
            } else if(!catalog.compareAndSetCurrent(new SkillCurrentPointerUpdate(scope,project,id,head.currentVersion(),head.currentSkillHash(),version,hash,
                    file.name(),description,file.content(),head.origin(),descriptor.packageHash(),descriptor.manifestJson(),artifactHashes)))
                throw new IllegalStateException("SKILL_FILE_PROJECTION_CONCURRENT_HEAD");
            var key=new SkillPackageKey(scope,project,id,version);
            var immutable=new SkillPackageVersion(0,key,hash,head==null?0:head.currentVersion(),head==null?"":head.currentSkillHash(),"","","","FILE_IMPORT",file.content(),
                    "FILE_PROJECTION",sourceHash,"Configured file snapshot",descriptor.packageHash(),descriptor.manifestJson(),artifactHashes,
                    descriptor.entrypoint(),descriptor.artifactCount(),descriptor.packageSize(),Instant.now());
            packages.appendVersion(immutable,descriptor.artifacts().values().stream()
                    .map(a->new SkillArtifact(a.path(),a.role(),a.mediaType(),a.encoding(),a.contentHash(),a.sizeBytes(),a.content())).toList());
            jdbc.update("UPDATE ai_ops_skill_file_projection SET source_hash=?,projected_version=?,projected_skill_hash=? WHERE scope=? AND project_id=? AND skill_id=?",
                    sourceHash,version,hash,scope,project,id);
        });
    }
    @Override public Set<String> managedIds(String scope,String project) {
        if(jdbc==null) throw new IllegalStateException("SKILL_FILE_PROJECTION_STORE_UNAVAILABLE");
        return Set.copyOf(jdbc.queryForList("""
            SELECT s.skill_id FROM ai_ops_skill_file_projection f JOIN ai_ops_skill s
              ON s.scope=f.scope AND s.project_id=f.project_id AND s.skill_id=f.skill_id
              AND s.current_version=f.projected_version AND s.current_skill_hash=f.projected_skill_hash
            WHERE f.scope=? AND f.project_id=?
            """,String.class,scope,project));
    }
}
