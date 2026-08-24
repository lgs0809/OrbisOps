package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Immutable page reviews. Proposal/lease/source locks are owned by the existing proposal adapter. */
final class JdbcSkillSourceBatchReview {
    private final JdbcTemplate jdbc;
    private final SkillSourceBatchPolicy policy=new SkillSourceBatchPolicy();
    JdbcSkillSourceBatchReview(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    Optional<Map<String,Object>> read(String plan,Map<String,Object> input,int index,String digest) {
        var page=page(input,index,digest);
        var rows=jdbc.queryForList("SELECT input_hash,review_json,review_hash FROM ai_ops_skill_evolution_batch_review WHERE plan_id=? AND batch_index=?",plan,index);
        if(rows.isEmpty()) return Optional.empty();
        var row=rows.get(0);String raw=text(row.get("review_json"));
        if(!digest.equals(row.get("input_hash"))||!hash(raw).equals(row.get("review_hash"))
                ||!CanonicalJson.stringify(object(raw)).equals(raw)) throw new IllegalStateException("SKILL_SOURCE_BATCH_HASH_MISMATCH");
        var result=object(raw);policy.validateReview(page,result);return Optional.of(result);
    }
    Map<String,Object> save(String plan,Map<String,Object> input,int index,String digest,Map<String,Object> review) {
        policy.validateReview(page(input,index,digest),review);
        String raw=CanonicalJson.stringify(review);
        jdbc.update("INSERT IGNORE INTO ai_ops_skill_evolution_batch_review(plan_id,batch_index,input_hash,review_json,review_hash) VALUES (?,?,?,?,?)",plan,index,digest,raw,hash(raw));
        var stored=read(plan,input,index,digest).orElseThrow();
        if(!CanonicalJson.stringify(stored).equals(raw)) throw new IllegalStateException("SKILL_SOURCE_BATCH_IMMUTABLE");
        return stored;
    }
    void requireComplete(String plan,Map<String,Object> input,Map<String,Object> authored) {
        var pages=policy.pages(input);if(pages.size()==1) return;
        var reviews=new ArrayList<Map<String,Object>>();
        for(int i=0;i<pages.size();i++) reviews.add(read(plan,input,i,CanonicalObjectHasher.sha256(pages.get(i)))
                .orElseThrow(()->new IllegalStateException("SKILL_SOURCE_BATCH_INCOMPLETE")));
        if(!CanonicalJson.stringify(policy.audit(input,reviews)).equals(CanonicalJson.stringify(authored.get("sourceBatchReviewAudit"))))
            throw new IllegalStateException("SKILL_SOURCE_BATCH_AUDIT_MISMATCH");
    }
    private Map<String,Object> page(Map<String,Object> input,int index,String digest) {
        var pages=policy.pages(input);
        if(pages.size()<2||index<0||index>=pages.size()||!CanonicalObjectHasher.sha256(pages.get(index)).equals(digest))
            throw new IllegalStateException("SKILL_SOURCE_BATCH_INPUT_MISMATCH");
        return pages.get(index);
    }
}
