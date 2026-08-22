package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalProofPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalUseCase;
import cn.lgs.orbisops.application.changepackage.ChangePackageAuditPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageCleanupPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageCleanupUseCase;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingGatePort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingJournalPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingLockPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRunPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRuntimePort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingSignalPort;
import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationPlanPort;
import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationProofPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.ChangePackageSkillSignalPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageTransactionPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationProofPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationWritebackUseCase;
import cn.lgs.orbisops.application.changepackage.LandChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalPort;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalSettings;
import cn.lgs.orbisops.application.changepackage.PrepareChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ReviewChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ValidateChangePackageUseCase;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageApprovalRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OpsChangePackageApplicationConfiguration {

    @Bean
    public LandingOperationJournalApplicationService landingOperationJournalApplicationService(
            LandingOperationJournalPort journalPort,
            ChangePackageTransactionPort transactionPort,
            @Value("${orbisops.approved-landing.operation-lease-seconds:300}") int leaseSeconds) {
        return new LandingOperationJournalApplicationService(
                journalPort,
                transactionPort,
                new LandingOperationJournalSettings(leaseSeconds),
                Clock.systemDefaultZone());
    }

    @Bean
    public ChangePackageApprovalUseCase changePackageApprovalUseCase(
            IChangePackageCurrentRepository currentRepository,
            IChangePackageVersionRepository versionRepository,
            IChangePackagePointerRepository pointerRepository,
            IChangePackageApprovalRepository approvalRepository,
            IChangePackageEventRepository eventRepository,
            ChangePackageApprovalProofPort proofPort,
            ChangePackageAuditPort auditPort,
            ChangePackageSkillSignalPort skillSignalPort,
            ChangePackageTransactionPort transactionPort) {
        return new ChangePackageApprovalUseCase(
                currentRepository,
                versionRepository,
                pointerRepository,
                approvalRepository,
                eventRepository,
                proofPort,
                auditPort,
                skillSignalPort,
                transactionPort);
    }

    @Bean
    public ChangePackageValidationWritebackUseCase changePackageValidationWritebackUseCase(
            IChangePackageCurrentRepository currentRepository,
            IChangePackageVersionRepository versionRepository,
            IChangePackagePointerRepository pointerRepository,
            IChangePackageEventRepository eventRepository,
            ChangePackageValidationProofPort proofPort,
            ChangePackageAuditPort auditPort,
            ChangePackageTransactionPort transactionPort) {
        return new ChangePackageValidationWritebackUseCase(
                currentRepository,
                versionRepository,
                pointerRepository,
                eventRepository,
                proofPort,
                auditPort,
                transactionPort);
    }

    @Bean
    public ChangePackageLandingProcessManager changePackageLandingProcessManager(
            IChangePackageCurrentRepository currentRepository,
            IChangePackageVersionRepository versionRepository,
            IChangePackagePointerRepository pointerRepository,
            IChangePackageEventRepository eventRepository,
            ChangePackageLandingGatePort gatePort,
            ChangePackageLandingRunPort runPort,
            ChangePackageLandingJournalPort journalPort,
            ChangePackageLandingLockPort lockPort,
            ChangePackageLandingRuntimePort runtimePort,
            ChangePackageAuditPort auditPort,
            ChangePackageLandingSignalPort signalPort,
            ToolExecutionIdempotencyPort toolExecutionLedger) {
        return new ChangePackageLandingProcessManager(
                currentRepository,
                versionRepository,
                pointerRepository,
                eventRepository,
                gatePort,
                runPort,
                journalPort,
                lockPort,
                runtimePort,
                auditPort,
                signalPort,
                toolExecutionLedger);
    }

    @Bean
    public ChangePackageCleanupUseCase changePackageCleanupUseCase(
            IChangePackageCurrentRepository currentRepository,
            IChangePackageEventRepository eventRepository,
            ChangePackageCleanupPort cleanupPort,
            ChangePackageAuditPort auditPort,
            ChangePackageTransactionPort transactionPort) {
        return new ChangePackageCleanupUseCase(
                currentRepository,
                eventRepository,
                cleanupPort,
                auditPort,
                transactionPort);
    }

    @Bean
    public PrepareChangePackageUseCase prepareChangePackageUseCase(
            ChangePackagePreparationPlanPort planPort,
            ChangePackagePreparationProofPort proofPort,
            IChangePackageCurrentRepository currentRepository,
            IChangePackageVersionRepository versionRepository,
            IChangePackagePointerRepository pointerRepository,
            IChangePackageEventRepository eventRepository,
            ChangePackageAuditPort auditPort,
            ChangePackageTransactionPort transactionPort,
            ChangePackageQueryPort queryPort) {
        return new PrepareChangePackageUseCase(
                planPort,
                proofPort,
                currentRepository,
                versionRepository,
                pointerRepository,
                eventRepository,
                auditPort,
                transactionPort,
                queryPort);
    }

    @Bean
    public ValidateChangePackageUseCase validateChangePackageUseCase(ChangePackageValidationPort port) {
        return new ValidateChangePackageUseCase(port);
    }

    @Bean
    public ReviewChangePackageUseCase reviewChangePackageUseCase(
            ChangePackageApprovalUseCase approvalUseCase,
            ChangePackageQueryPort queryPort) {
        return new ReviewChangePackageUseCase(approvalUseCase, queryPort);
    }

    @Bean
    public LandChangePackageUseCase landChangePackageUseCase(
            ChangePackageLandingProcessManager landingProcessManager,
            ChangePackageCleanupUseCase cleanupUseCase,
            ChangePackageQueryPort queryPort) {
        return new LandChangePackageUseCase(landingProcessManager, cleanupUseCase, queryPort);
    }

    @Bean
    public ChangePackageQueryService changePackageQueryService(ChangePackageQueryPort port) {
        return new ChangePackageQueryService(port);
    }
}
