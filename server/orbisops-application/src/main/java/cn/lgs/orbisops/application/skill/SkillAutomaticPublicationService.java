package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.*;
import java.util.List;
import java.util.Map;

/** Source-qualified publication. No per-Skill case quota, shadow score or canary admission. */
public final class SkillAutomaticPublicationService {
    public static final String POLICY = "source-qualified-auto-v1";
    private final SkillReleasePort releases;
    private final SkillPatchCandidateApplicationService candidates;
    private final SkillPatchValidationApplicationService validation;
    private final SkillAutomaticPublicationCheckPort checks;
    private final SkillContentReviewPort review;
    private final SkillReleasePackageAssembler assembler;
    private final SkillManagementUseCase management;
    private final SkillTransactionPort transactions;
    private final SkillReleaseStateCoordinator states;
    private final SkillReleaseSettings settings;
    private SkillAtomicPublicationPort atomic;

    public SkillAutomaticPublicationService withAtomicPublication(SkillAtomicPublicationPort atomic) {
        this.atomic=java.util.Objects.requireNonNull(atomic);return this;
    }

    public SkillAutomaticPublicationService(SkillReleasePort releases,
            SkillPatchCandidateApplicationService candidates, SkillPatchValidationApplicationService validation,
            SkillAutomaticPublicationCheckPort checks, SkillContentReviewPort review,
            SkillReleasePackageAssembler assembler, SkillManagementUseCase management,
            SkillTransactionPort transactions, SkillReleaseAuditPort audit, SkillReleaseSettings settings) {
        this.releases=releases; this.candidates=candidates; this.validation=validation;
        this.checks=checks; this.review=review; this.assembler=assembler; this.management=management;
        this.transactions=transactions; this.settings=settings;
        this.states=new SkillReleaseStateCoordinator(releases,candidates,audit);
    }

    public SkillReleaseStartOutcome start(String candidateId) {
        return start(candidateId,()->{});
    }

    public SkillReleaseStartOutcome start(String candidateId,Runnable leaseGuard) {
        try {return startCurrent(candidateId,leaseGuard);}
        catch(RuntimeException failure) {
            if(!admission.stale(failure))throw failure;
            return rejectUnpublished(candidateId,leaseGuard,failure);
        }
    }

    private final cn.lgs.orbisops.domain.skill.service.SkillPublicationAdmissionPolicy admission=
            new cn.lgs.orbisops.domain.skill.service.SkillPublicationAdmissionPolicy();

    private SkillReleaseStartOutcome startCurrent(String candidateId,Runnable leaseGuard) {
        leaseGuard.run();
        if (!settings.enabled()) return SkillReleaseStartOutcome.disabled(candidateId);
        var existing=releases.findByCandidate(candidateId);
        if (existing.isPresent()) {
            advance(existing.get(),leaseGuard);
            return SkillReleaseStartOutcome.released(releases.findByCandidate(candidateId).orElseThrow());
        }
        var candidate=candidates.getCandidate(candidateId);
        boolean replacement=SkillAtomicPublicationPlan.supports(candidate.patchType());
        if (!java.util.Set.of("CREATE","CREATE_SKILL","UPDATE_ROUTING_RULE","UPDATE_DIAGNOSTIC_RECIPE",
                "UPDATE_EVIDENCE_CRITERIA","UPDATE_NEGATIVE_RULE").contains(candidate.patchType()) && !(replacement && atomic!=null))
            return SkillReleaseStartOutcome.validationRejected(candidateId,validation.validateMinimumDecision(candidateId,
                    List.of("POLICY_ATOMIC_LIFECYCLE_PUBLICATION_REQUIRED")));
        var decision=validation.validateMinimumDecision(candidateId,List.of());
        if (!decision.valid()) return SkillReleaseStartOutcome.validationRejected(candidateId,decision);
        // No model call is held inside a database transaction.
        var input=transactions.required(() -> checks.requireCurrentSources(candidate)).input();
        var deterministicFailures=new java.util.ArrayList<>(new cn.lgs.orbisops.domain.skill.service.SkillMinimumContentPolicy().evaluate(candidate,input));
        SkillAtomicPublicationPlan plan=null;
        if(replacement) {
            try {
                plan=SkillAtomicPublicationPlan.from(candidate,input);
                for(var target:plan.targets()) deterministicFailures.addAll(new cn.lgs.orbisops.domain.skill.service.SkillPatchValidationPolicy()
                        .evaluate(plan.branch(candidate,target),List.of()).failures());
            } catch(IllegalArgumentException invalid) {deterministicFailures.add(invalid.getMessage());}
        }
        if (!deterministicFailures.isEmpty()) return SkillReleaseStartOutcome.validationRejected(candidateId,
                validation.validateMinimumDecision(candidateId,deterministicFailures));
        var failures=review.review(candidate,input);
        decision=validation.validateMinimumDecision(candidateId,failures);
        if (!decision.valid()) return SkillReleaseStartOutcome.validationRejected(candidateId,decision);
        var metadata=new java.util.LinkedHashMap<String,Object>();metadata.put("publicationPolicy",POLICY);
        if(plan!=null) metadata.put("atomicPlan",cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(
                cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(plan)));
        var ready=new SkillReleaseSnapshot("skill-release-"+CanonicalObjectHasher.sha256Text(candidateId).substring(0,32),
                candidateId,candidate.projectId(),candidate.agentId(),candidate.targetSkillId(),
                SkillReleaseStatus.READY,0,candidate.baseSkillVersion(),candidate.baseSkillHash(),
                "MINIMUM_CHECKS_PASSED",0,"",metadata);
        transactions.required(() -> {
            leaseGuard.run();
            checks.requireCurrentSources(candidate);
            releases.create(ready);
            return Boolean.TRUE;
        });
        advance(releases.findByCandidate(candidateId).orElseThrow(),leaseGuard);
        return SkillReleaseStartOutcome.released(releases.findByCandidate(candidateId).orElseThrow());
    }

    public void advance(SkillReleaseSnapshot release) {
        try {advance(release,()->{});}
        catch(RuntimeException failure) {
            if(!admission.stale(failure))throw failure;
            rejectUnpublished(release.candidateId(),()->{},failure);
        }
    }

    /** The failed publication transaction has rolled back. Only a never-published READY release may close here. */
    private SkillReleaseStartOutcome rejectUnpublished(String candidateId,Runnable leaseGuard,RuntimeException failure) {
        return transactions.required(()->{
            leaseGuard.run();var current=releases.findByCandidate(candidateId);String reason=admission.reason(failure);
            if(current.isEmpty()) return SkillReleaseStartOutcome.validationRejected(candidateId,
                    validation.validateMinimumDecision(candidateId,List.of(reason)));
            var release=current.orElseThrow();
            if(release.status()!=SkillReleaseStatus.READY) throw failure;
            if(!states.move(release,SkillReleaseStatus.READY,SkillReleaseStatus.ROLLED_BACK,reason,0,"",""))
                throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
            return SkillReleaseStartOutcome.released(releases.findByCandidate(candidateId).orElseThrow());
        });
    }

    private void advance(SkillReleaseSnapshot release,Runnable leaseGuard) {
        if (!POLICY.equals(release.metadata().get("publicationPolicy"))) return;
        if (release.status()==SkillReleaseStatus.READY) {
            // Claim, immutable package, catalog CAS and release receipt commit together. A crash retries READY.
            transactions.required(() -> {
                leaseGuard.run();
                var candidate=candidates.getCandidate(release.candidateId());
                var input=checks.requireCurrentSources(candidate).input();
                if (!releases.claim(release.releaseId(),SkillReleaseStatus.READY,SkillReleaseStatus.PROMOTING)) return Boolean.TRUE;
                var promoting=release.transitionTo(SkillReleaseStatus.PROMOTING);
                if(SkillAtomicPublicationPlan.supports(candidate.patchType())) {
                    if(atomic==null) throw new IllegalStateException("SKILL_ATOMIC_PUBLICATION_UNAVAILABLE");
                    var plan=SkillAtomicPublicationPlan.from(candidate,input);
                    var outcomes=new java.util.ArrayList<SkillPublicationOutcome>();
                    for(var target:plan.targets()) {
                        var patch=assembler.patch(plan.branch(candidate,target));
                        newSkill(patch,target.skillId(),target.name());
                        var outcome=management.createProjectSkillOutcome(candidate.projectId(),patch,"SYSTEM_SKILL_EVOLVER");
                        if(!outcome.published()) throw new IllegalStateException("SKILL_ATOMIC_PACKAGE_REJECTED:"+outcome.reasonCode());
                        outcomes.add(outcome);
                    }
                    atomic.stage(candidate.candidateId(),plan,outcomes);
                    if(!states.move(promoting,SkillReleaseStatus.PROMOTING,SkillReleaseStatus.PENDING_INDEX,
                            "WAITING_FOR_ALL_BODIES_AND_INDEXES",0,"","")) throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
                    return Boolean.TRUE;
                }
                var patch=assembler.patch(candidate);
                SkillPublicationOutcome published;
                if (candidate.targetSkillId().isBlank()) {
                    newSkill(patch,"evolved-"+candidate.candidateHash().substring(0,16),"自动沉淀运维方法");
                    published=management.createProjectSkillOutcome(candidate.projectId(),patch,"SYSTEM_SKILL_EVOLVER");
                } else {
                    published=management.publishEvolvedProjectSkillOutcome(candidate.projectId(),candidate.targetSkillId(),
                            patch,candidate.baseSkillVersion(),candidate.baseSkillHash(),"SYSTEM_SKILL_EVOLVER");
                }
                if (!states.move(promoting,SkillReleaseStatus.PROMOTING,
                        published.published()?SkillReleaseStatus.PENDING_INDEX:SkillReleaseStatus.ROLLED_BACK,
                        published.published()?"WAITING_FOR_BODY_AND_INDEX":published.reasonCode(),
                        published.version(),published.skillHash(),published.skillId()))
                    throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
                return Boolean.TRUE;
            });
        } else if (release.status()==SkillReleaseStatus.PENDING_INDEX) {
            transactions.required(() -> {
                leaseGuard.run();
                boolean ready=release.metadata().containsKey("atomicPlan")
                        ? atomic!=null && atomic.active(release.candidateId()) : checks.runtimeReady(release);
                if (ready) states.move(release,SkillReleaseStatus.PENDING_INDEX,SkillReleaseStatus.ACTIVE,
                        "BODY_AND_INDEX_READY",release.releasedVersion(),release.releasedSkillHash(),release.targetSkillId());
                return Boolean.TRUE;
            });
        }
    }

    public void rollbackAtomic(String project,String candidateId,String actor,String reason) {
        if(atomic==null) throw new IllegalStateException("SKILL_ATOMIC_PUBLICATION_UNAVAILABLE");
        transactions.required(() -> {
            var release=releases.findByCandidate(candidateId).orElseThrow();
            if(!project.equals(release.projectId())) throw new SecurityException("SKILL_ATOMIC_PROJECT_MISMATCH");
            if(!release.metadata().containsKey("atomicPlan")) throw new IllegalArgumentException("SKILL_ATOMIC_PUBLICATION_REQUIRED");
            if(release.status()==SkillReleaseStatus.ROLLED_BACK) return Boolean.TRUE;
            if(release.status()!=SkillReleaseStatus.ACTIVE && release.status()!=SkillReleaseStatus.PENDING_INDEX)
                throw new IllegalStateException("SKILL_ATOMIC_ROLLBACK_STATE_INVALID");
            var previous=release.status();
            if(previous==SkillReleaseStatus.ACTIVE) {
                if(!releases.claim(release.releaseId(),previous,SkillReleaseStatus.ROLLING_BACK)) throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
                release=release.transitionTo(SkillReleaseStatus.ROLLING_BACK);
            }
            atomic.rollback(project,candidateId,actor,reason);
            if(!states.move(release,release.status(),SkillReleaseStatus.ROLLED_BACK,"ATOMIC_ROLLBACK",0,"",""))
                throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
            return Boolean.TRUE;
        });
    }

    private void newSkill(Map<String,Object> patch,String id,String name) {
        patch.put("skillId",id);patch.put("name",name);patch.put("origin","EVOLVED");patch.put("status","ENABLED");
        patch.put("updateMode","AUTO");patch.put("autoUpdateEnabled",true);patch.put("publishMode","AUTO_EVOLVER");
    }
}
