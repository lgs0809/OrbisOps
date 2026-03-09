package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageAggregate;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationFailure;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationWriteback;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageValidationExecutionToken;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageValidationWritebackFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Transactional process manager for pre-approval validation result writeback. */
public final class ChangePackageValidationWritebackUseCase {

    private static final ChangePackageValidationExecutionToken EXECUTION_TOKEN =
            new ChangePackageValidationExecutionToken();
    private static final ChangePackageValidationWritebackFactory WRITEBACK_FACTORY =
            new ChangePackageValidationWritebackFactory();

    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageVersionRepository versionRepository;
    private final IChangePackagePointerRepository pointerRepository;
    private final IChangePackageEventRepository eventRepository;
    private final ChangePackageValidationProofPort proofPort;
    private final ChangePackageAuditPort auditPort;
    private final ChangePackageTransactionPort transactionPort;

    public ChangePackageValidationWritebackUseCase(IChangePackageCurrentRepository currentRepository,
                                                   IChangePackageVersionRepository versionRepository,
                                                   IChangePackagePointerRepository pointerRepository,
                                                   IChangePackageEventRepository eventRepository,
                                                   ChangePackageValidationProofPort proofPort,
                                                   ChangePackageAuditPort auditPort,
                                                   ChangePackageTransactionPort transactionPort) {
        this.currentRepository = required(currentRepository, "CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.versionRepository = required(versionRepository, "CHANGE_PACKAGE_VERSION_REPOSITORY_REQUIRED");
        this.pointerRepository = required(pointerRepository, "CHANGE_PACKAGE_POINTER_REPOSITORY_REQUIRED");
        this.eventRepository = required(eventRepository, "CHANGE_PACKAGE_EVENT_REPOSITORY_REQUIRED");
        this.proofPort = required(proofPort, "CHANGE_PACKAGE_VALIDATION_PROOF_PORT_REQUIRED");
        this.auditPort = required(auditPort, "CHANGE_PACKAGE_AUDIT_PORT_REQUIRED");
        this.transactionPort = required(transactionPort, "CHANGE_PACKAGE_TRANSACTION_PORT_REQUIRED");
    }

    public String executionToken(String packageId, int version, String packageHash) {
        return EXECUTION_TOKEN.issue(packageId, version, packageHash);
    }

    public ChangePackageValidationOutcome writeBack(ChangePackageCommands.ValidationWriteback command) {
        ChangePackageCommands.ValidationWriteback requiredCommand = required(
                command, "CHANGE_PACKAGE_VALIDATION_WRITEBACK_COMMAND_REQUIRED");
        return transactionPort.required(() -> writeBackInTransaction(requiredCommand));
    }

    private ChangePackageValidationOutcome writeBackInTransaction(ChangePackageCommands.ValidationWriteback command) {
        ChangePackageCurrent current = current(command.packageId());
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(current.pointer());
        aggregate.requireValidationOutcome(command.passed());
        ChangePackageValidationReport validationReport = command.validationReport();
        String reasonCode = validationReport.effectiveReasonCode(command.passed());
        Map<String, Object> report = new LinkedHashMap<>(validationReport.eventPayload(command.passed()));

        if (!command.passed()) {
            ChangePackageStatus nextStatus = aggregate.validationFailed().status();
            ChangePackageValidationFailure failure = new ChangePackageValidationFailure(
                    "NEEDS_REFINEMENT", reasonCode, report);
            if (!pointers().compareAndSetValidationFailure(current.pointer(), failure)) {
                throw new IllegalStateException("ChangePackage 状态或版本已漂移，validation failed CAS 失败："
                        + current.packageId());
            }
            appendEvent(current.packageId(), "PACKAGE_VALIDATION_FAILED", command.actor(),
                    "ChangePackage 审核前验证失败", report);
            auditPort.record(current.projectId(), "validation-failed", current.packageId(), current, report);
            return new ChangePackageValidationOutcome(current.packageId(), nextStatus,
                    current.version(), current.packageHash(), false, reasonCode);
        }

        EXECUTION_TOKEN.verify(validationReport.executionToken(),
                current.packageId(), current.version(), current.packageHash());
        ChangePackageVersion sourceVersion = version(current.packageId(), current.version());
        ChangePackageValidationWriteback writeback = WRITEBACK_FACTORY.preparePassed(
                current, sourceVersion, report, command.actor());
        proofPort.verifySourceProofs(current, sourceVersion, report);
        proofPort.recordWritebackProof(current, writeback, command.actor());
        if (!pointers().compareAndSetVersion(current.pointer(), writeback.nextPointer(),
                writeback.snapshot().currentState())) {
            throw new IllegalStateException("ChangePackage 状态或版本已漂移，validation proof writeback CAS 失败："
                    + current.packageId());
        }
        versions().append(new ChangePackageVersion(
                0L,
                current.packageId(),
                writeback.targetVersion(),
                writeback.targetPackageHash(),
                ChangePackageStatus.READY_FOR_REVIEW.name(),
                writeback.snapshot(),
                "validation proof writeback",
                command.actor(),
                null));
        Map<String, Object> payload = writeback.snapshot().toMap();
        appendEvent(current.packageId(), "PACKAGE_VALIDATION_PASSED", command.actor(),
                "ChangePackage 已通过审核前验证，并生成包含 trusted proof 的新版本 v"
                        + writeback.targetVersion(), payload);
        auditPort.record(current.projectId(), "validation-passed", current.packageId(), current, payload);
        return new ChangePackageValidationOutcome(current.packageId(), ChangePackageStatus.READY_FOR_REVIEW,
                writeback.targetVersion(), writeback.targetPackageHash(), true, writeback.reasonCode());
    }

    private void appendEvent(String packageId,
                             String eventType,
                             String actor,
                             String summary,
                             Map<String, Object> payload) {
        events().append(new ChangePackageEvent(
                0L,
                "cpe-" + UUID.randomUUID(),
                packageId,
                eventType,
                actor,
                summary,
                payload,
                null));
    }

    private ChangePackageCurrent current(String packageId) {
        IChangePackageCurrentRepository repository = currentStore();
        return repository.find(packageId)
                .orElseThrow(() -> new IllegalArgumentException("ChangePackage 不存在：" + packageId));
    }

    private ChangePackageVersion version(String packageId, int version) {
        IChangePackageVersionRepository repository = versions();
        return repository.find(packageId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ChangePackage 版本不存在：" + packageId + "@" + version));
    }

    private IChangePackageCurrentRepository currentStore() {
        if (!currentRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
        return currentRepository;
    }

    private IChangePackageVersionRepository versions() {
        if (!versionRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_VERSION_STORE_UNAVAILABLE");
        return versionRepository;
    }

    private IChangePackagePointerRepository pointers() {
        if (!pointerRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_POINTER_STORE_UNAVAILABLE");
        return pointerRepository;
    }

    private IChangePackageEventRepository events() {
        if (!eventRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_EVENT_STORE_UNAVAILABLE");
        return eventRepository;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }
}
