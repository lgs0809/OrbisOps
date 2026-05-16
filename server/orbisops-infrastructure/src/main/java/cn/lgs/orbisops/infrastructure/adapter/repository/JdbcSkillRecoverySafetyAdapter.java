package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillRecoverySafetyPort;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Restoration needs a verified prior release and resolvable, unchanged dependency versions. */
@Repository
public class JdbcSkillRecoverySafetyAdapter implements SkillRecoverySafetyPort {
    private final JdbcTemplate jdbc;
    public JdbcSkillRecoverySafetyAdapter(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean safeToRestore(SkillReleaseSnapshot release, SkillPackageVersion baseline) {
        if (!"PROJECT".equals(baseline.key().scope()) || !release.projectId().equals(baseline.key().projectId())
                || !release.targetSkillId().equals(baseline.key().skillId())
                || release.baselineVersion() != baseline.key().version()
                || !release.baselineSkillHash().equals(baseline.skillHash())) return false;
        var prior = jdbc.queryForList("""
                SELECT candidate_id FROM ai_ops_skill_release WHERE project_id=? AND target_skill_id=?
                AND released_version=? AND released_skill_hash=? AND status='ACTIVE' AND release_id<>? LIMIT 2
                """, release.projectId(), release.targetSkillId(), baseline.key().version(), baseline.skillHash(), release.releaseId());
        if (prior.size() != 1) return false;
        var previous = new JdbcSkillReleaseAdapter(jdbc).findByCandidate(String.valueOf(prior.get(0).get("candidate_id")));
        if (previous.isEmpty() || !new JdbcSkillCanaryEvidenceReader(jdbc).read(previous.get()).promotable()) return false;
        return dependenciesCompatible(release.projectId(), baseline.manifestJson(), new HashSet<>(), 0);
    }

    boolean dependenciesCompatible(String project, String manifestJson, Set<String> visiting, int depth) {
        if (depth > 16) return false;
        try {
            var manifest = CanonicalJson.parseObject(manifestJson);
            if (!(manifest.get("dependencies") instanceof List<?> dependencies)) return false;
            if (dependencies.size() > 32) return false;
            for (Object item : dependencies) {
                if (!(item instanceof Map<?, ?> dependency) || !"SKILL".equals(dependency.get("type"))) return false;
                String id = String.valueOf(dependency.get("id")), version = String.valueOf(dependency.get("version"));
                if (!version.matches("[1-9][0-9]{0,8}") || !visiting.add(id)) return false;
                var rows = jdbc.queryForList("""
                        SELECT package_manifest_json FROM ai_ops_skill WHERE scope='PROJECT' AND project_id=? AND skill_id=?
                        AND current_version=? AND lifecycle_status='ACTIVE' AND execution_mode='ENABLED'
                        AND legacy_frozen_classification_required=0 FOR UPDATE
                        """, project, id, Integer.parseInt(version));
                if (rows.size() != 1 || !dependenciesCompatible(project, String.valueOf(rows.get(0).get("package_manifest_json")), visiting, depth+1)) return false;
                visiting.remove(id);
            }
            return true;
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
