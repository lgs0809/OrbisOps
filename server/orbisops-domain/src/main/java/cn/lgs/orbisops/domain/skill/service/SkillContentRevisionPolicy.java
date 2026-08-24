package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.util.*;

/** A correction may repair method content, never its operation, ownership or evidence population. */
public final class SkillContentRevisionPolicy {
    public static final int MAX_ROUNDS=2;
    public SkillPatchCandidate revised(SkillPatchCandidate before,Map<String,Object> input,
            Map<String,Object> authored,int round,String planHash) {
        if(round<1 || round>MAX_ROUNDS || !"LLM".equals(authored.get("authoringSource"))
                || !"gpt-6.1-terra".equals(authored.get("authoringModel"))
                || !before.patchType().equals(authored.get("patchType"))
                || !before.targetSkillId().equals(authored.get("targetSkillId"))
                || !before.riskLevel().name().equals(authored.get("riskLevel"))) fail();
        var next=new SkillPatchCandidate(before.candidateId(),CanonicalObjectHasher.sha256(Map.of(
                "previousCandidateHash",before.candidateHash(),"planHash",planHash,"round",round,"authored",authored)),
                before.sourceRunId(),before.sourceType(),before.projectId(),before.agentId(),before.scope(),
                before.targetSkillId(),before.patchType(),before.riskLevel(),before.baseSkillVersion(),before.baseSkillHash(),
                before.contextBundleHash(),before.evidenceRefs(),list(authored.get("changes")),list(authored.get("artifacts")),
                list(authored.get("evalCases")),SkillPatchCandidateStatus.CANDIDATE,"",before.createdAt(),null);
        if(!new SkillPatchValidationPolicy().evaluate(next,List.of()).valid()
                || !new SkillMinimumContentPolicy().evaluate(next,input).isEmpty()) fail();
        if(SkillAtomicPublicationPlan.supports(before.patchType())) {
            var old=SkillAtomicPublicationPlan.from(before,input);var updated=SkillAtomicPublicationPlan.from(next,input);
            if(!old.sources().equals(updated.sources()) || old.targets().size()!=updated.targets().size()) fail();
            for(var target:old.targets()) if(updated.targets().stream().noneMatch(t->t.key().equals(target.key())
                    && t.sourceIds().equals(target.sourceIds()))) fail();
        }
        return next;
    }
    private List<Object> list(Object value) {
        if(!(value instanceof List<?> values)) {fail();return List.of();}
        return List.copyOf(values);
    }
    private void fail() {throw new IllegalArgumentException("POLICY_CONTENT_REVISION_SCOPE_CHANGED");}
}
