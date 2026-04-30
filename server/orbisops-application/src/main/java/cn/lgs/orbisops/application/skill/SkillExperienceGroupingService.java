package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import java.util.*;

/** Runs only in the durable background worker. Remote calls never hold a MySQL transaction. */
public final class SkillExperienceGroupingService {
    private final SkillExperienceGroupingStore store;
    private final SkillExperienceGroupingModelPort model;
    private final SkillExperienceGroupingIndexPort index;
    private final SkillExperienceEmbeddingPort embeddings;
    private final SkillExperiencePort experience;
    public SkillExperienceGroupingService(SkillExperienceGroupingStore store,SkillExperienceGroupingModelPort model,
            SkillExperienceGroupingIndexPort index,SkillExperienceEmbeddingPort embeddings,SkillExperiencePort experience) {
        this.store=store;this.model=model;this.index=index;this.embeddings=embeddings;this.experience=experience;
    }
    public SkillExperienceRecordResult group(SkillEvolutionPipelineRequest request,SkillExperienceRecordResult observed) {
        try { return process(request,observed); }
        catch(RuntimeException error) {
            if(Set.of("SKILL_EVOLUTION_SOURCE_REVOKED","SKILL_EVOLUTION_CLAIM_LOST").contains(String.valueOf(error.getMessage()))) throw error;
            throw new SkillGroupingDeferredException(error);
        }
    }
    private SkillExperienceRecordResult process(SkillEvolutionPipelineRequest request,SkillExperienceRecordResult observed) {
        var claim=Objects.requireNonNull(request.jobClaim(),"SKILL_GROUPING_CLAIM_REQUIRED");
        String hash=request.summary().sourceHash();
        var fact=store.fact(claim,hash).orElseGet(()->store.saveFact(claim,hash,model.extract(request.summary().episodeJson())));
        Group group=store.assigned(claim,hash).orElse(null);
        if(group==null) {
            String document=fact.method().document();
            var refs=index.search(request.projectId(),document,embeddings.modelIdentity(),embeddings.embed(document,true));
            if(refs.size()>5) throw new IllegalStateException("SKILL_GROUPING_RECALL_LIMIT");
            var compared=new ArrayList<Group>();
            for(var ref:refs) {
                var current=store.current(request.projectId(),ref.groupId());
                if(current.isPresent()) { project(request.projectId(),current.get());projectIndex(current.get());compared.add(current.get()); }
            }
            var decision=model.decide(fact,List.copyOf(compared));
            if("APPEND".equals(decision.action()) && compared.stream().noneMatch(g->g.groupId().equals(decision.groupId())))
                throw new IllegalStateException("SKILL_GROUPING_UNKNOWN_GROUP");
            if("REVIEW".equals(decision.action())) {
                store.defer(claim,hash,decision);
                throw new IllegalStateException("SKILL_GROUPING_MORE_EVIDENCE_REQUIRED");
            }
            group=store.commit(claim,hash,observed,decision,compared);
        }
        project(request.projectId(),group);projectIndex(group);
        var o=observed.observation();
        var grouped=new SkillExperienceObservation(o.episodeId(),o.observationId(),group.groupId(),o.projectId(),o.agentId(),o.runId(),
                o.sessionId(),o.observationType(),o.taskTemplate(),o.taskTemplateHash(),o.abstractTrajectory(),o.trajectoryHash(),
                o.outcome(),o.evidenceReferences(),o.finalSummary(),o.eventCount(),o.qualityScore(),o.verifiedTaskOutcome());
        return new SkillExperienceRecordResult(grouped,experience.cluster(o.projectId(),o.agentId(),group.groupId()),observed.newObservation());
    }
    private void projectIndex(Group group) {
        String identity=embeddings.modelIdentity();
        if(index.contains(group,identity)) return;
        if(group.sources().isEmpty()) throw new IllegalStateException("SKILL_GROUPING_SOURCES_EMPTY");
        var vectors=new ArrayList<float[]>();
        for(var fact:group.sources()) {
            var cached=index.factEmbedding(group.projectId(),fact,identity);
            float[] vector=cached.orElseGet(()->{
                float[] encoded=embeddings.embed(fact.method().document(),false);
                index.cacheFactEmbedding(group.projectId(),fact,identity,encoded);
                return encoded;
            });
            vectors.add(vector);
        }
        // Individual facts remain immutable authority; a centroid is only a recall representation.
        index.put(group,identity,SkillExperienceVectorProjection.centroid(vectors));
    }
    private void project(String project,Group group) {
        if(!project.equals(group.projectId())) throw new SecurityException("SKILL_GROUPING_PROJECT_MISMATCH");
    }
}
