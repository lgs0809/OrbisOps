package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;

@Repository
public class JdbcSkillAutomaticPublicationChecks implements SkillAutomaticPublicationCheckPort {
    private final JdbcTemplate jdbc;
    private final JdbcSkillCatalogRepository catalog;
    private final SkillRouteIndexPort index;
    public JdbcSkillAutomaticPublicationChecks(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc,
            JdbcSkillCatalogRepository catalog, SkillRouteIndexPort index) {
        this.jdbc=jdbc; this.catalog=catalog; this.index=index;
    }
    @Override public SkillPublicationSources requireCurrentSources(SkillPatchCandidate candidate) {
        return new SkillPublicationSources(new JdbcSkillEvolutionProposalAdapter(jdbc).publicationInput(candidate));
    }
    @Override public boolean runtimeReady(SkillReleaseSnapshot release) {
        var current=catalog.lockEvolutionEntry("PROJECT",release.projectId(),release.targetSkillId()).orElse(null);
        if(current==null || !current.governanceState().activeAtUse()
                || current.currentVersion()!=release.releasedVersion()
                || !current.currentSkillHash().equals(release.releasedSkillHash())) return false;
        var rows=jdbc.queryForList("""
                SELECT model_identity FROM ai_ops_skill_runtime_publication
                WHERE scope='PROJECT' AND project_id=? AND skill_id=? AND skill_version=? AND skill_hash=? AND package_hash=?
                """,release.projectId(),release.targetSkillId(),release.releasedVersion(),release.releasedSkillHash(),current.currentPackageHash());
        var runtime=SkillCatalogSnapshot.fromView(new SkillCatalogViewMapper().toView(current,false)).runtimeCandidate();
        return rows.stream().anyMatch(r -> index.contains(runtime,String.valueOf(r.get("model_identity"))));
    }
}
