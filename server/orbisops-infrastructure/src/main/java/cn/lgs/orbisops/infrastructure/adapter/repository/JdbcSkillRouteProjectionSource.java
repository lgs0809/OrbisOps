package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillRouteProjectionSourcePort;
import cn.lgs.orbisops.domain.skill.model.*;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class JdbcSkillRouteProjectionSource implements SkillRouteProjectionSourcePort {
    private final JdbcSkillCatalogRepository catalog;
    public JdbcSkillRouteProjectionSource(JdbcSkillCatalogRepository catalog) { this.catalog=catalog; }
    @Override public List<SkillCatalogEntry> page(long afterId,int limit) { return catalog.routingMetadataPage(afterId,Math.max(1,Math.min(100,limit))); }
    @Override public boolean current(SkillRuntimeCandidate c) {
        return catalog.find(c.scope(),c.projectId(),c.skillId(),false).filter(now->now.governanceState().activeAtUse()
                && now.currentVersion()==c.version() && now.currentSkillHash().equals(c.skillHash()) && now.currentPackageHash().equals(c.packageHash())).isPresent();
    }
}
