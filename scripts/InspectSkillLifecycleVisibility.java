package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.*;

/** Read effective authoring references and the actual old publication state through the exact deployed module. */
class InspectSkillLifecycleVisibility {
    public static void main(String[] args) throws Exception {
        var input=CanonicalJson.parseObject(new String(System.in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        String project="ops-acceptance-a",candidate="skill-candidate-6242fe80-03ca-4e67-a4bd-d96a64c5b2fe";
        try(var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13362/orbisops_acceptance?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                "root",System.getenv("OPS_ACCEPTANCE_READ_PASSWORD"))){
            c.setReadOnly(true);c.setAutoCommit(false);var jdbc=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            jdbc.execute("START TRANSACTION READ ONLY");
            var catalog=new JdbcSkillCatalogRepository(jdbc);
            var ids=catalog.findAuthoringMetadata("PROJECT",project).stream().map(e->e.skillId()).toList();
            var release=jdbc.queryForMap("SELECT release_id,status,reason_code,released_version,metadata_json FROM ai_ops_skill_release WHERE candidate_id=? AND project_id=?",candidate,project);
            var state=jdbc.queryForMap("SELECT status,candidate_hash FROM ai_ops_skill_patch_candidate WHERE candidate_id=? AND project_id=?",candidate,project);
            var proposal=jdbc.queryForMap("SELECT plan_id,plan_hash,input_json,authored_hash,authored_json FROM ai_ops_skill_evolution_proposal WHERE candidate_id=? AND project_id=?",candidate,project);
            if(!CanonicalObjectHasher.sha256Text(String.valueOf(proposal.get("input_json"))).equals(proposal.get("plan_hash"))
                    ||!CanonicalObjectHasher.sha256Text(String.valueOf(proposal.get("authored_json"))).equals(proposal.get("authored_hash")))
                throw new IllegalStateException("ORIGINAL_PROPOSAL_HASH_CHANGED");
            var row=new LinkedHashMap<String,Object>();row.put("effectiveAuthoringSkillIds",ids);row.put("release",release);row.put("candidate",state);
            row.put("originalPlanId",proposal.get("plan_id"));row.put("originalPlanHash",proposal.get("plan_hash"));row.put("originalAuthoredHash",proposal.get("authored_hash"));
            row.put("job",jdbc.queryForMap("SELECT status,attempts,last_error FROM ai_ops_skill_evolution_job WHERE job_id='skill-evo-3f0f165d81e04e7f7311d00a73ac2d7a' AND project_id=?",project));
            row.put("atomicGroups",jdbc.queryForList("SELECT candidate_id,status,operation FROM ai_ops_skill_atomic_publication WHERE project_id=? ORDER BY candidate_id",project));
            row.put("scope","READ_ONLY_EXISTING_OLD_PROPOSAL_AND_EFFECTIVE_METHODS_NO_NEW_PUBLICATION");
            if(Boolean.TRUE.equals(input.get("verifyClosed"))){
                if(ids.contains("evolved-cf3d6ea6ffadeeba8be923d4")||!ids.contains("synthetic-discovery-status-duplicate-a"))
                    throw new IllegalStateException("ROLLED_BACK_METHOD_STILL_SELECTED");
                if(!"ROLLED_BACK".equals(release.get("status"))||!"ROLLED_BACK".equals(state.get("status"))
                        ||!String.valueOf(release.get("reason_code")).startsWith("POLICY_PUBLICATION_BASELINE_STALE:"))
                    throw new IllegalStateException("OLD_UNPUBLISHED_RELEASE_NOT_CLOSED");
                row.put("status","PASS_EFFECTIVE_VISIBILITY_AND_AUTOMATIC_STALE_CLOSE");
            }else row.put("status","READ_ONLY_BEFORE_FIX_BASELINE");
            c.rollback();System.out.println(CanonicalJson.stringify(row));
        }
    }
}
