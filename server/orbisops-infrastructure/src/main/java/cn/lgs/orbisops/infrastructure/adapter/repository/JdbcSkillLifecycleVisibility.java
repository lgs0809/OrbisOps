package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Function;

/** One effective replacement/rollback view shared by runtime and authoring reads. */
final class JdbcSkillLifecycleVisibility {
    private final JdbcTemplate jdbc;
    JdbcSkillLifecycleVisibility(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    <T> List<T> filter(String project,List<T> rows,Function<T,String> scope,Function<T,String> id,Set<String> explicit) {
        if(rows.stream().noneMatch(r->"PROJECT".equals(scope.apply(r)))) return List.copyOf(rows);
        var visibility=jdbc.queryForList("""
            SELECT m.skill_id,'TARGET' AS role FROM ai_ops_skill_atomic_member m JOIN ai_ops_skill_atomic_publication g ON g.candidate_id=m.candidate_id
            WHERE m.project_id=? AND m.role='TARGET' AND g.status<>'ACTIVE'
            UNION ALL SELECT source_skill_id,'SOURCE' AS role FROM ai_ops_skill_atomic_replacement WHERE project_id=?
            """,project,project);
        var targets=new HashSet<String>();var sources=new HashSet<String>();
        for(var row:visibility) ("TARGET".equals(row.get("role"))?targets:sources).add(String.valueOf(row.get("skill_id")));
        return rows.stream().filter(r->!"PROJECT".equals(scope.apply(r))||!targets.contains(id.apply(r))
                &&(!sources.contains(id.apply(r))||explicit.contains(id.apply(r)))).toList();
    }
}
