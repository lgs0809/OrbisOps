package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillContentRevisionPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillAuthoringAgent;
import org.springframework.stereotype.Component;
import java.util.*;

/** Background correction of the same proposal, capped across restarts and concurrent workers. */
@Component
public final class OpsSkillContentRevisionAdapter implements SkillContentRevisionPort {
    private final SkillContentRevisionStorePort store;
    private final OpsSkillAuthoringAgent author;
    public OpsSkillContentRevisionAdapter(SkillContentRevisionStorePort store,OpsSkillAuthoringAgent author) {this.store=store;this.author=author;}
    @Override public boolean revise(SkillPatchCandidate candidate,Map<String,Object> input,List<String> failures,Runnable guard) {
        for(int i=0;i<SkillContentRevisionPolicy.MAX_ROUNDS;i++) {
            guard.run();var next=store.claim(candidate,input,failures);if(next.isEmpty()) return false;
            var claim=next.orElseThrow();
            try {
                var progress=new SkillAuthoringProgressPort() {
                    public Optional<Map<String,Object>> read(int index,String hash) {
                        var pages=new SkillSourceBatchPolicy().pages(claim.input());
                        if(index<0 || index>=claim.batchReviews().size() || !CanonicalObjectHasher.sha256(pages.get(index)).equals(hash))
                            throw new IllegalStateException("SKILL_SOURCE_BATCH_INPUT_MISMATCH");
                        return Optional.of(claim.batchReviews().get(index));
                    }
                    public Map<String,Object> save(int index,String hash,Map<String,Object> review) {
                        throw new IllegalStateException("SKILL_SOURCE_BATCH_IMMUTABLE");
                    }
                };
                var result=author.revise(claim.input(),progress,Map.of("round",claim.round(),"maxRounds",2,
                        "previousCandidate",claim.previous(),"contentReviewFailures",claim.failures()));
                guard.run();if(store.complete(claim,result)) return true;
            } catch(RuntimeException failure) {
                store.defer(claim);
                if(failure instanceof SkillContentReviewUnavailableException deferred) throw deferred;
                throw new SkillContentReviewUnavailableException(failure);
            }
        }
        return false;
    }
}
