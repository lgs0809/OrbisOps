package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillMaintenancePolicy;
import java.time.Instant;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class SkillMaintenanceApplicationService {
    private final SkillMaintenancePort store;
    private final SkillCatalogQueryService catalog;
    private final SkillManagementUseCase management;
    private final SkillTransactionPort tx;
    private final SkillCompressionReviewPort review;
    private final SkillMaintenancePolicy policy = new SkillMaintenancePolicy();
    public SkillMaintenanceApplicationService(SkillMaintenancePort store, SkillCatalogQueryService catalog,
            SkillManagementUseCase management, SkillTransactionPort tx, SkillCompressionReviewPort review) {
        this.store=store; this.catalog=catalog; this.management=management; this.tx=tx; this.review=review;
    }

    public long scanBatch(long afterId, ToIntFunction<String> counter, String tokenizer, Instant now) {
        var batch=store.scan(afterId,100);
        for(var item:batch) {
            var artifacts=catalog.listSkillArtifacts(item.projectId(),item.skillId(),item.version(),
                    item.skillHash(),item.packageHash(),"PROJECT");
            int tokens=new SkillMaintenancePackage(item.content(),artifacts).estimatedReadTokens(counter);
            if(policy.compressionDue(tokens,item.patches(),item.checkedPatches()))
                store.enqueue(item,"COMPRESS_CHECK",tokens,tokenizer,tokens>2000?"BODY_OVER_2000_TOKENS":"FIVE_NEW_PATCHES");
            if(policy.inactivityDue(item.createdAt(),item.lastUseAt(),now))
                store.enqueue(item,"INACTIVITY_REVIEW",tokens,tokenizer,"NO_RECORDED_USE_FOR_90_DAYS");
        }
        return batch.size()<100?0:batch.get(batch.size()-1).id();
    }

    public void checkNext() {
        store.claim().ifPresent(claim -> {
            try {check(claim);}
            catch(RuntimeException transientFailure) {store.defer(claim,"MAINTENANCE_RECHECK_REQUIRED");}
        });
    }

    private void check(SkillMaintenancePort.Claim claim) {
        var subject=claim.subject();
        var head=catalog.getProjectSkill(subject.projectId(),subject.skillId());
        if(!(head.get("currentVersion") instanceof Number v) || v.intValue()!=subject.version()
                || !subject.skillHash().equals(head.get("currentSkillHash"))
                || !subject.packageHash().equals(head.get("currentPackageHash"))) {
            store.finish(claim,"SUPERSEDED","BASE_VERSION_CHANGED",Map.of(),0);return;
        }
        if(!subject.automatic()) {
            store.finish(claim,"REVIEW_REQUIRED","MANUAL_MAINTENANCE_ONLY",Map.of(),0);return;
        }
        // These reads validate the full immutable version and package hashes. No transaction spans the model call.
        var version=catalog.getSkillVersion(subject.projectId(),subject.skillId(),subject.version(),
                subject.skillHash(),subject.packageHash(),"PROJECT");
        var artifacts=catalog.listSkillArtifacts(subject.projectId(),subject.skillId(),subject.version(),
                subject.skillHash(),subject.packageHash(),"PROJECT");
        var packageText=new SkillMaintenancePackage(subject.content(),artifacts);
        var proposal=review.propose(version,packageText.evidence());
        Map<String,String> changes;
        try {changes=packageText.validate(proposal);}
        catch(IllegalArgumentException unsafe) {
            store.finish(claim,"REVIEW_REQUIRED","BEHAVIOR_CHANGE_REQUIRES_SOURCE_QUALIFIED_PATCH",
                    Map.of("proposal",proposal,"guardReason",unsafe.getMessage()),0);return;
        }
        if(changes.isEmpty()) {
            store.finish(claim,"NO_CHANGE","NO_EQUIVALENT_SIMPLIFICATION",Map.of("proposal",proposal),0);return;
        }
        var reviewed=new java.util.LinkedHashMap<>(review.review(version,packageText.evidence(),changes));
        reviewed.put("proposal",proposal);
        if(!Boolean.TRUE.equals(reviewed.get("equivalent")) || !Boolean.TRUE.equals(reviewed.get("safe"))) {
            store.finish(claim,"REVIEW_REQUIRED","BEHAVIOR_CHANGE_REQUIRES_SOURCE_QUALIFIED_PATCH",reviewed,0);return;
        }
        tx.required(() -> {
            store.requireCurrent(claim);
            var mutation=packageText.mutation(changes);
            mutation.put("evolutionJobId",claim.id());
            mutation.put("changeSummary","COMPRESS: equivalent method consolidation after separate content review");
            var result=management.publishEvolvedProjectSkillOutcome(subject.projectId(),subject.skillId(),mutation,
                    subject.version(),subject.skillHash(),"SYSTEM_SKILL_MAINTENANCE");
            store.finish(claim,result.published()?"PENDING_INDEX":"SUPERSEDED",result.reasonCode(),reviewed,result.version());
            return Boolean.TRUE;
        });
    }
}
