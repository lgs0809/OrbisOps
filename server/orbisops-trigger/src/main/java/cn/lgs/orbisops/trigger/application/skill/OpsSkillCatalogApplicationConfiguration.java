package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.ForkSealedSkillUseCase;
import cn.lgs.orbisops.application.skill.LockSkillUseCase;
import cn.lgs.orbisops.application.skill.QuarantineSkillUseCase;
import cn.lgs.orbisops.application.skill.RestoreSkillUseCase;
import cn.lgs.orbisops.application.skill.SealSkillUseCase;
import cn.lgs.orbisops.application.skill.SelectRuntimeSkillsQuery;
import cn.lgs.orbisops.application.skill.SkillRuntimePublishedVersionPort;
import cn.lgs.orbisops.application.skill.SkillGovernanceAuditPort;
import cn.lgs.orbisops.application.skill.SkillGovernanceAuthorizationPort;
import cn.lgs.orbisops.application.skill.SkillGovernanceTransitionUseCase;
import cn.lgs.orbisops.application.skill.UnlockSkillUseCase;
import cn.lgs.orbisops.application.skill.SkillCatalogMutationUseCase;
import cn.lgs.orbisops.application.skill.SkillCatalogPort;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillCatalogReadService;
import cn.lgs.orbisops.application.skill.SkillCanaryApplicationService;
import cn.lgs.orbisops.application.skill.SkillCanarySettings;
import cn.lgs.orbisops.application.skill.SkillEvolutionApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuditPort;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoringPort;
import cn.lgs.orbisops.application.skill.SkillEvolutionSourcePort;
import cn.lgs.orbisops.application.skill.SkillEvolutionJobApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionPatchJsonEncoder;
import cn.lgs.orbisops.application.skill.SkillEvolutionPayloadCodec;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelineAuditPort;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelinePort;
import cn.lgs.orbisops.application.skill.SkillEvolutionPromotionSettings;
import cn.lgs.orbisops.application.skill.SkillEvolutionPublishUseCase;
import cn.lgs.orbisops.application.skill.SkillEvolutionSimilarityPort;
import cn.lgs.orbisops.application.skill.SkillBehaviorReplayApplicationService;
import cn.lgs.orbisops.application.skill.SkillBehaviorReplayPort;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionPort;
import cn.lgs.orbisops.application.skill.SkillCandidateBehaviorReplayPort;
import cn.lgs.orbisops.application.skill.SkillCandidateTournament;
import cn.lgs.orbisops.application.skill.SkillCandidateTournamentSettings;
import cn.lgs.orbisops.application.skill.SkillEvolutionCandidateSetSelector;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSetPort;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteApplicationService;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuitePort;
import cn.lgs.orbisops.application.skill.SkillModelJudgePort;
import cn.lgs.orbisops.application.skill.SkillStructuralVerifierPort;
import cn.lgs.orbisops.application.skill.SkillConfusionGraphPort;
import cn.lgs.orbisops.application.skill.SkillDefectDiagnosisApplicationService;
import cn.lgs.orbisops.application.skill.SkillProgressivePackageAssembler;
import cn.lgs.orbisops.application.skill.SkillLifecycleApplicationService;
import cn.lgs.orbisops.application.skill.SkillLifecyclePort;
import cn.lgs.orbisops.application.skill.SkillRoutingConfusionApplicationService;
import cn.lgs.orbisops.application.skill.SkillDefectDiagnosisPort;
import cn.lgs.orbisops.application.skill.SkillOptimizationMemoryApplicationService;
import cn.lgs.orbisops.application.skill.SkillOptimizationMemoryPort;
import cn.lgs.orbisops.application.skill.SkillOptimizationRunApplicationService;
import cn.lgs.orbisops.application.skill.SkillOptimizationRunPort;
import cn.lgs.orbisops.application.skill.SkillEffectMetricApplicationService;
import cn.lgs.orbisops.application.skill.SkillEffectMetricPort;
import cn.lgs.orbisops.application.skill.SkillExperienceApplicationService;
import cn.lgs.orbisops.application.skill.SkillExperienceAuditPort;
import cn.lgs.orbisops.application.skill.SkillExperiencePort;
import cn.lgs.orbisops.application.skill.SkillEvolutionSignalApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionTraceInputPort;
import cn.lgs.orbisops.application.skill.SkillFileSourcePort;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.application.skill.SkillPackageQueryService;
import cn.lgs.orbisops.application.skill.SkillPatchCandidateApplicationService;
import cn.lgs.orbisops.application.skill.SkillPatchCandidatePort;
import cn.lgs.orbisops.application.skill.SkillPatchRegressionEvaluationPort;
import cn.lgs.orbisops.application.skill.SkillPatchValidationApplicationService;
import cn.lgs.orbisops.application.skill.SkillPatchValidationResultPort;
import cn.lgs.orbisops.application.skill.SkillProjectValidationPort;
import cn.lgs.orbisops.application.skill.SkillRollbackUseCase;
import cn.lgs.orbisops.application.skill.SkillCanaryContextApplicationService;
import cn.lgs.orbisops.application.skill.SkillReleaseApplicationService;
import cn.lgs.orbisops.application.skill.SkillReleaseAuditPort;
import cn.lgs.orbisops.application.skill.SkillReleasePackageAssembler;
import cn.lgs.orbisops.application.skill.SkillReleasePort;
import cn.lgs.orbisops.application.skill.SkillReleaseSettings;
import cn.lgs.orbisops.application.skill.SkillRerankPort;
import cn.lgs.orbisops.application.skill.SkillRuntimeSelectionSettings;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageApplicationService;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsagePort;
import cn.lgs.orbisops.application.skill.SkillShadowApplicationService;
import cn.lgs.orbisops.application.skill.SkillShadowEvalCasePort;
import cn.lgs.orbisops.application.skill.SkillShadowModelPort;
import cn.lgs.orbisops.application.skill.SkillShadowSettings;
import cn.lgs.orbisops.application.skill.SkillSemanticScorePort;
import cn.lgs.orbisops.application.skill.SkillTransactionPort;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionJobRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionSignalRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionInputPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionOpportunityPolicy;
import cn.lgs.orbisops.domain.skill.model.SkillEffectThresholds;
import cn.lgs.orbisops.domain.skill.service.SkillCanarySelectionPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEffectDegradationPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSignalPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillExperienceObservationPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import cn.lgs.orbisops.domain.skill.service.SkillPatchValidationPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillShadowDecisionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class OpsSkillCatalogApplicationConfiguration {

    @Bean
    public SkillPackageManifest.Limits skillPackageLimits(
            @Value("${orbisops.skill-package.max-artifacts:32}") int maxArtifacts,
            @Value("${orbisops.skill-package.max-artifact-bytes:262144}") long maxArtifactBytes,
            @Value("${orbisops.skill-package.max-package-bytes:2097152}") long maxPackageBytes) {
        long safeArtifactBytes = Math.max(1024L, maxArtifactBytes);
        return new SkillPackageManifest.Limits(
                Math.max(1, maxArtifacts),
                safeArtifactBytes,
                Math.max(safeArtifactBytes, maxPackageBytes));
    }

    @Bean
    public SkillCatalogMutationUseCase skillCatalogMutationUseCase(
            ISkillCatalogRepository catalogRepository,
            ISkillPackageRepository packageRepository,
            SkillTransactionPort transactionPort,
            SkillProjectValidationPort projectValidationPort,
            SkillPackageManifest.Limits packageLimits) {
        return new SkillCatalogMutationUseCase(
                catalogRepository, packageRepository, transactionPort,
                projectValidationPort, packageLimits);
    }

    @Bean
    public SkillRollbackUseCase skillRollbackUseCase(
            ISkillCatalogRepository catalogRepository,
            ISkillPackageRepository packageRepository,
            SkillTransactionPort transactionPort,
            SkillPackageManifest.Limits packageLimits,
            cn.lgs.orbisops.application.skill.SkillRecoverySafetyPort recoverySafety) {
        return new SkillRollbackUseCase(
                catalogRepository, packageRepository, transactionPort, packageLimits, recoverySafety);
    }

    @Bean
    public SkillGovernanceTransitionUseCase skillGovernanceTransitionUseCase(
            ISkillCatalogRepository catalogRepository,
            SkillTransactionPort transactionPort,
            SkillGovernanceAuthorizationPort authorizationPort,
            SkillGovernanceAuditPort auditPort) {
        return new SkillGovernanceTransitionUseCase(
                catalogRepository, transactionPort, authorizationPort, auditPort);
    }

    @Bean
    public LockSkillUseCase lockSkillUseCase(SkillGovernanceTransitionUseCase transitions) {
        return new LockSkillUseCase(transitions);
    }

    @Bean
    public UnlockSkillUseCase unlockSkillUseCase(SkillGovernanceTransitionUseCase transitions) {
        return new UnlockSkillUseCase(transitions);
    }

    @Bean
    public SealSkillUseCase sealSkillUseCase(SkillGovernanceTransitionUseCase transitions) {
        return new SealSkillUseCase(transitions);
    }

    @Bean
    public QuarantineSkillUseCase quarantineSkillUseCase(SkillGovernanceTransitionUseCase transitions) {
        return new QuarantineSkillUseCase(transitions);
    }

    @Bean
    public RestoreSkillUseCase restoreSkillUseCase(
            ISkillCatalogRepository catalogRepository,
            ISkillPackageRepository packageRepository,
            SkillTransactionPort transactionPort,
            SkillPackageManifest.Limits packageLimits,
            SkillGovernanceAuthorizationPort authorizationPort,
            SkillGovernanceAuditPort auditPort) {
        return new RestoreSkillUseCase(catalogRepository, packageRepository, transactionPort,
                packageLimits, authorizationPort, auditPort);
    }

    @Bean
    public ForkSealedSkillUseCase forkSealedSkillUseCase(
            ISkillCatalogRepository catalogRepository,
            ISkillPackageRepository packageRepository,
            SkillTransactionPort transactionPort,
            SkillPackageManifest.Limits packageLimits,
            SkillGovernanceAuthorizationPort authorizationPort,
            SkillGovernanceAuditPort auditPort) {
        return new ForkSealedSkillUseCase(catalogRepository, packageRepository, transactionPort,
                packageLimits, authorizationPort, auditPort);
    }

    @Bean
    public SkillEvolutionPublishUseCase skillEvolutionPublishUseCase(
            ISkillCatalogRepository catalogRepository,
            ISkillPackageRepository packageRepository,
            SkillTransactionPort transactionPort,
            SkillPackageManifest.Limits packageLimits) {
        return new SkillEvolutionPublishUseCase(
                catalogRepository, packageRepository, transactionPort, packageLimits);
    }

    @Bean
    public SkillCatalogPort skillCatalogPort(
            ISkillCatalogRepository catalogRepository,
            ISkillPackageRepository packageRepository,
            SkillFileSourcePort fileSourcePort,
            SkillProjectValidationPort projectValidationPort, cn.lgs.orbisops.application.skill.SkillFileProjectionPort fileProjection) {
        return new SkillCatalogReadService(catalogRepository, packageRepository,
                fileSourcePort, projectValidationPort, fileProjection);
    }

    @Bean
    public SkillPackageQueryService skillPackageQueryService(
            SkillCatalogPort port,
            ISkillPackageRepository packageRepository,
            SkillProjectValidationPort projectValidationPort) {
        return new SkillPackageQueryService(port, packageRepository, projectValidationPort);
    }

    @Bean
    public SkillManagementUseCase skillManagementUseCase(
            SkillCatalogPort port,
            SkillCatalogMutationUseCase mutationUseCase,
            SkillRollbackUseCase rollbackUseCase,
            SkillEvolutionPublishUseCase evolutionPublishUseCase,
            SkillPackageQueryService packageQueryService) {
        return new SkillManagementUseCase(
                port, mutationUseCase, rollbackUseCase, evolutionPublishUseCase, packageQueryService);
    }

    @Bean
    public SkillCatalogQueryService skillCatalogQueryService(
            SkillCatalogPort port,
            SkillPackageQueryService packageQueryService,SkillRuntimePublishedVersionPort runtimePublication) {
        return new SkillCatalogQueryService(port, packageQueryService,runtimePublication);
    }

    @Bean
    public SkillEvolutionSignalApplicationService skillEvolutionSignalApplicationService(
            ISkillEvolutionSignalRepository repository,
            OpsSkillEvolutionSignalIdentityAdapter identityAdapter,
            OpsSkillEvolutionSignalAuditAdapter auditAdapter) {
        return new SkillEvolutionSignalApplicationService(
                repository,
                new SkillEvolutionSignalPolicy(),
                identityAdapter,
                auditAdapter);
    }

    @Bean
    public SkillHiddenEvaluationSuiteApplicationService skillHiddenEvaluationSuiteApplicationService(
            SkillHiddenEvaluationSuitePort port) {
        return new SkillHiddenEvaluationSuiteApplicationService(port);
    }

    @Bean
    public SkillCandidateTournamentSettings skillCandidateTournamentSettings(
            @Value("${orbisops.skill-evolution.tournament.mode:LEGACY_PRIMARY}") String mode,
            @Value("${orbisops.skill-evolution.tournament.hidden-suite-version:v1}") String hiddenSuiteVersion,
            @Value("${orbisops.skill-evolution.tournament.structural-version:structural-v1}") String structuralVersion,
            @Value("${orbisops.skill-evolution.tournament.behavior-version:behavior-v1}") String behaviorVersion,
            @Value("${orbisops.skill-evolution.tournament.judge-version:judge-v1}") String judgeVersion) {
        return SkillCandidateTournamentSettings.from(
                mode,
                hiddenSuiteVersion,
                structuralVersion,
                behaviorVersion,
                judgeVersion);
    }

    @Bean
    public SkillCandidateTournament skillCandidateTournament(
            SkillStructuralVerifierPort structuralVerifier,
            SkillHiddenEvaluationSetPort hiddenEvaluationSetPort,
            SkillCandidateBehaviorReplayPort behaviorReplay,
            SkillModelJudgePort modelJudge) {
        return new SkillCandidateTournament(
                structuralVerifier,
                hiddenEvaluationSetPort,
                behaviorReplay,
                modelJudge);
    }

    @Bean
    public SkillEvolutionCandidateSetSelector skillEvolutionCandidateSetSelector(
            SkillCandidateTournament tournament) {
        return new SkillEvolutionCandidateSetSelector(tournament);
    }

    @Bean
    public SkillEvolutionApplicationService skillEvolutionApplicationService(
            SkillEvolutionSignalApplicationService signalService,
            SkillExperienceApplicationService experienceService,
            SkillPatchCandidateApplicationService candidateService,
            SkillReleaseApplicationService releaseService,
            SkillEvolutionAuthoringPort authoringPort,
            SkillEvolutionSimilarityPort similarityPort,
            SkillEvolutionPipelineAuditPort pipelineAuditPort,
            SkillEvolutionCandidateSetSelector candidateSetSelector,
            SkillCandidateTournamentSettings tournamentSettings,
            cn.lgs.orbisops.application.skill.SkillEvolutionProposalPort proposalPort,
            cn.lgs.orbisops.application.skill.SkillExperienceGroupingService grouping,
            @Value("${orbisops.skill-evolution.promotion.min-observations:3}") int minimumObservations,
            @Value("${orbisops.skill-evolution.promotion.explicit-procedure-min-observations:2}") int explicitProcedureMinimumObservations,
            @Value("${orbisops.skill-evolution.promotion.min-distinct-runs:2}") int minimumDistinctRuns,
            @Value("${orbisops.skill-evolution.promotion.min-distinct-sessions:2}") int minimumDistinctSessions,
            @Value("${orbisops.skill-evolution.promotion.consolidation-sample-limit:12}") int consolidationSampleLimit) {
        return new SkillEvolutionApplicationService(
                signalService,
                experienceService,
                candidateService,
                releaseService,
                authoringPort,
                similarityPort,
                pipelineAuditPort,
                new SkillEvolutionPayloadCodec(),
                new SkillEvolutionOpportunityPolicy(),
                new SkillEvolutionPromotionSettings(
                        minimumObservations,
                        explicitProcedureMinimumObservations,
                        minimumDistinctRuns,
                        minimumDistinctSessions,
                        consolidationSampleLimit),
                candidateSetSelector,
                tournamentSettings,proposalPort).withGrouping(grouping);
    }

    @Bean
    public cn.lgs.orbisops.application.skill.SkillEvolutionRelatedSkillService skillEvolutionRelatedSkillService(
            cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository repository,
            cn.lgs.orbisops.application.skill.SkillFileSourcePort files,
            cn.lgs.orbisops.application.skill.SkillCatalogQueryService catalog) {
        return new cn.lgs.orbisops.application.skill.SkillEvolutionRelatedSkillService(repository,files,catalog);
    }

    @Bean
    public SkillEvolutionJobApplicationService skillEvolutionJobApplicationService(
            ISkillEvolutionJobRepository repository,
            SkillEvolutionSourcePort sourcePort,
            SkillEvolutionPipelinePort pipelinePort,
            SkillEvolutionAuditPort auditPort,
            cn.lgs.orbisops.application.skill.SkillEvolutionLeasePort leases) {
        return new SkillEvolutionJobApplicationService(
                repository,
                new SkillEvolutionJobPolicy(),
                new SkillEvolutionInputPolicy(),
                sourcePort,
                pipelinePort,
                new SkillEvolutionPatchJsonEncoder(),
                () -> "skill-patch-" + UUID.randomUUID(),
                auditPort,
                Clock.systemDefaultZone(), leases);
    }

    @Bean
    public SkillPatchCandidateApplicationService skillPatchCandidateApplicationService(
            SkillPatchCandidatePort candidatePort) {
        return new SkillPatchCandidateApplicationService(candidatePort);
    }

    @Bean
    public SkillDefectDiagnosisApplicationService skillDefectDiagnosisApplicationService(
            SkillDefectDiagnosisPort port) {
        return new SkillDefectDiagnosisApplicationService(port, Clock.systemUTC()::instant);
    }

    @Bean
    public SkillOptimizationMemoryApplicationService skillOptimizationMemoryApplicationService(
            SkillOptimizationMemoryPort port) {
        return new SkillOptimizationMemoryApplicationService(port, Clock.systemUTC()::instant);
    }

    @Bean
    public SkillOptimizationRunApplicationService skillOptimizationRunApplicationService(
            SkillOptimizationRunPort port) {
        return new SkillOptimizationRunApplicationService(port, Clock.systemUTC()::instant);
    }

    @Bean
    public SkillBehaviorReplayApplicationService skillBehaviorReplayApplicationService(
            SkillBehaviorReplayPort replayPort,
            SkillBehaviorToolExecutionPort toolExecutionPort) {
        return new SkillBehaviorReplayApplicationService(replayPort, toolExecutionPort);
    }

    @Bean
    public SkillRoutingConfusionApplicationService skillRoutingConfusionApplicationService(
            SkillConfusionGraphPort graphPort) {
        return new SkillRoutingConfusionApplicationService(graphPort);
    }

    @Bean
    public SkillProgressivePackageAssembler skillProgressivePackageAssembler() {
        return new SkillProgressivePackageAssembler();
    }

    @Bean
    public SkillLifecycleApplicationService skillLifecycleApplicationService(
            SkillLifecyclePort port) {
        return new SkillLifecycleApplicationService(port);
    }

    @Bean
    public SkillPatchValidationApplicationService skillPatchValidationApplicationService(
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchRegressionEvaluationPort regressionPort,
            SkillPatchValidationResultPort validationResultPort) {
        return new SkillPatchValidationApplicationService(
                candidateService,
                regressionPort,
                validationResultPort,
                new SkillPatchValidationPolicy());
    }

    @Bean
    public SkillReleasePackageAssembler skillReleasePackageAssembler(
            SkillCatalogQueryService catalogQueryService) {
        return new SkillReleasePackageAssembler(catalogQueryService);
    }

    @Bean
    public SkillCanaryApplicationService skillCanaryApplicationService(
            @Value("${orbisops.skill-evolution.canary.enabled:true}") boolean enabled,
            @Value("${orbisops.skill-evolution.canary-percent:10}") int percent) {
        return new SkillCanaryApplicationService(
                new SkillCanarySelectionPolicy(),
                new SkillCanarySettings(enabled, percent));
    }

    @Bean
    public SkillCanaryContextApplicationService skillCanaryContextApplicationService(
            SkillReleasePort releasePort,
            SkillCanaryApplicationService canaryService) {
        return new SkillCanaryContextApplicationService(
                releasePort,
                canaryService);
    }

    @Bean
    public SkillShadowApplicationService skillShadowApplicationService(
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchRegressionEvaluationPort regressionPort,
            SkillShadowModelPort modelPort,
            SkillShadowEvalCasePort evalCasePort,
            @Value("${orbisops.skill-evolution.shadow.enabled:true}") boolean enabled,
            @Value("${orbisops.skill-evolution.shadow.min-score:0.80}") double minimumScore) {
        return new SkillShadowApplicationService(
                candidateService,
                regressionPort,
                modelPort,
                evalCasePort,
                new SkillShadowDecisionPolicy(),
                new SkillShadowSettings(enabled, minimumScore));
    }

    @Bean
    public cn.lgs.orbisops.application.skill.SkillExperienceGroupingService skillExperienceGroupingService(
            cn.lgs.orbisops.application.skill.SkillExperienceGroupingStore store,
            cn.lgs.orbisops.application.skill.SkillExperienceGroupingModelPort model,
            cn.lgs.orbisops.application.skill.SkillExperienceGroupingIndexPort index,
            cn.lgs.orbisops.application.skill.SkillExperienceEmbeddingPort embeddings,
            SkillExperiencePort experience) {
        return new cn.lgs.orbisops.application.skill.SkillExperienceGroupingService(store,model,index,embeddings,experience);
    }

    @Bean
    public SkillExperienceApplicationService skillExperienceApplicationService(
            SkillExperiencePort experiencePort,
            SkillExperienceAuditPort auditPort,
            SkillTransactionPort transactionPort,
            cn.lgs.orbisops.application.skill.SkillTaskOutcomePort outcomes) {
        return new SkillExperienceApplicationService(
                experiencePort,
                auditPort,
                transactionPort,
                new SkillExperienceObservationPolicy(), outcomes);
    }

    @Bean
    public SkillEffectMetricApplicationService skillEffectMetricApplicationService(
            SkillEffectMetricPort metricPort,
            @Value("${orbisops.skill-evolution.min-sample-size:10}") int minSampleSize,
            @Value("${orbisops.skill-evolution.rollback-thresholds.blocked-tool-rate:0.25}") double maxBlockedRate,
            @Value("${orbisops.skill-evolution.rollback-thresholds.needs-replan-rate:0.30}") double maxReplanRate,
            @Value("${orbisops.skill-evolution.rollback-thresholds.negative-feedback-rate:0.20}") double maxNegativeRate,
            @Value("${orbisops.skill-evolution.rollback-thresholds.success-rate-drop:0.15}") double maxSuccessRateDrop,
            @Value("${orbisops.skill-evolution.rollback-thresholds.evidence-rate-drop:0.15}") double maxEvidenceRateDrop,
            @Value("${orbisops.skill-evolution.rollback-thresholds.tool-call-increase-rate:0.50}") double maxToolCallIncreaseRate) {
        return new SkillEffectMetricApplicationService(
                metricPort,
                new SkillEffectDegradationPolicy(),
                new SkillEffectThresholds(
                        minSampleSize,
                        maxBlockedRate,
                        maxReplanRate,
                        maxNegativeRate,
                        maxSuccessRateDrop,
                        maxEvidenceRateDrop,
                        maxToolCallIncreaseRate));
    }

    @Bean
    public SkillReleaseApplicationService skillReleaseApplicationService(
            SkillReleasePort releasePort,
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchValidationApplicationService validationService,
            SkillShadowApplicationService shadowService,
            SkillCanaryApplicationService canaryService,
            SkillEffectMetricApplicationService metricService,
            SkillManagementUseCase managementUseCase,
            SkillReleasePackageAssembler packageAssembler,
            SkillReleaseAuditPort auditPort,
            @Value("${orbisops.skill-evolution.enabled:true}") boolean enabled,
            @Value("${orbisops.skill-evolution.min-sample-size:10}") int minimumSampleSize,
            @Value("${orbisops.skill-evolution.release.batch-size:20}") int evaluationBatchSize,
            cn.lgs.orbisops.application.skill.SkillAutomaticPublicationCheckPort publicationChecks,
            cn.lgs.orbisops.application.skill.SkillContentReviewPort contentReview,
            cn.lgs.orbisops.application.skill.SkillAtomicPublicationPort atomicPublication,
            SkillTransactionPort transactionPort) {
        return new SkillReleaseApplicationService(
                releasePort,
                candidateService,
                validationService,
                shadowService,
                canaryService,
                metricService,
                managementUseCase,
                packageAssembler,
                auditPort,
                new SkillReleaseSettings(
                        enabled,
                        minimumSampleSize,
                        evaluationBatchSize)).withAutomaticPublication(
                new cn.lgs.orbisops.application.skill.SkillAutomaticPublicationService(
                        releasePort, candidateService, validationService, publicationChecks, contentReview,
                        packageAssembler, managementUseCase, transactionPort, auditPort,
                        new SkillReleaseSettings(enabled, minimumSampleSize, evaluationBatchSize)).withAtomicPublication(atomicPublication));
    }

    @Bean
    public SkillRuntimeUsageApplicationService skillRuntimeUsageApplicationService(
            SkillRuntimeUsagePort usagePort,
            SkillEffectMetricApplicationService metricService,
            SkillTransactionPort transactionPort) {
        return new SkillRuntimeUsageApplicationService(
                usagePort,
                metricService,
                transactionPort);
    }

    @Bean
    public SkillRuntimeSelectionSettings skillRuntimeSelectionSettings(
            @Value("${orbisops.skill-runtime.catalog-candidate-limit:24}") int catalogCandidateLimit,
            @Value("${orbisops.skill-runtime.selected-limit:6}") int selectedLimit,
            @Value("${orbisops.skill-runtime.max-explicit-skills:12}") int maxExplicitSkills,
            @Value("${orbisops.skill-runtime.min-relevance-score:0.12}") double minRelevanceScore,
            @Value("${orbisops.skill-runtime.similar-suppression-threshold:0.88}") double similarSuppressionThreshold,
            @Value("${orbisops.skill-runtime.semantic-weight:0.42}") double semanticWeight,
            @Value("${orbisops.skill-runtime.semantic-recall-limit:64}") int semanticRecallLimit,
            @Value("${orbisops.skill-runtime.category-boost:0.12}") double categoryBoost,
            @Value("${orbisops.skill-runtime.negative-penalty-weight:0.65}") double negativePenaltyWeight,
            @Value("${orbisops.skill-runtime.llm-rerank-enabled:true}") boolean llmRerankEnabled,
            @Value("${orbisops.skill-runtime.llm-rerank-candidate-limit:10}") int llmRerankCandidateLimit,
            @Value("${orbisops.skill-runtime.llm-rerank-weight:0.35}") double llmRerankWeight) {
        return new SkillRuntimeSelectionSettings(
                catalogCandidateLimit, selectedLimit, maxExplicitSkills,
                minRelevanceScore, similarSuppressionThreshold, semanticWeight,
                semanticRecallLimit, categoryBoost, negativePenaltyWeight,
                llmRerankEnabled, llmRerankCandidateLimit, llmRerankWeight);
    }

    @Bean
    public SelectRuntimeSkillsQuery selectRuntimeSkillsQuery(
            SkillCatalogPort port,
            SkillSemanticScorePort semanticScorePort,
            SkillRerankPort rerankPort,
            SkillRuntimeSelectionSettings settings,
            cn.lgs.orbisops.application.skill.SkillRuntimePublishedVersionPort publishedVersions,
            cn.lgs.orbisops.application.skill.SkillApplicabilityPort applicability) {
        return new SelectRuntimeSkillsQuery(port, semanticScorePort, rerankPort, settings, publishedVersions, applicability);
    }
}
