package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageApprovalRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageAggregate;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApproval;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalAssessment;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageApprovalAssessmentFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Transactional application orchestration for submit-review, approve and reject. */
public final class ChangePackageApprovalUseCase {

    private static final ChangePackageApprovalAssessmentFactory ASSESSMENT_FACTORY =
            new ChangePackageApprovalAssessmentFactory();

    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageVersionRepository versionRepository;
    private final IChangePackagePointerRepository pointerRepository;
    private final IChangePackageApprovalRepository approvalRepository;
    private final IChangePackageEventRepository eventRepository;
    private final ChangePackageApprovalProofPort proofPort;
    private final ChangePackageAuditPort auditPort;
    private final ChangePackageSkillSignalPort skillSignalPort;
    private final ChangePackageTransactionPort transactionPort;

    public ChangePackageApprovalUseCase(IChangePackageCurrentRepository currentRepository,
                                        IChangePackageVersionRepository versionRepository,
                                        IChangePackagePointerRepository pointerRepository,
                                        IChangePackageApprovalRepository approvalRepository,
                                        IChangePackageEventRepository eventRepository,
                                        ChangePackageApprovalProofPort proofPort,
                                        ChangePackageAuditPort auditPort,
                                        ChangePackageSkillSignalPort skillSignalPort,
                                        ChangePackageTransactionPort transactionPort) {
        this.currentRepository = required(currentRepository, "CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.versionRepository = required(versionRepository, "CHANGE_PACKAGE_VERSION_REPOSITORY_REQUIRED");
        this.pointerRepository = required(pointerRepository, "CHANGE_PACKAGE_POINTER_REPOSITORY_REQUIRED");
        this.approvalRepository = required(approvalRepository, "CHANGE_PACKAGE_APPROVAL_REPOSITORY_REQUIRED");
        this.eventRepository = required(eventRepository, "CHANGE_PACKAGE_EVENT_REPOSITORY_REQUIRED");
        this.proofPort = required(proofPort, "CHANGE_PACKAGE_APPROVAL_PROOF_PORT_REQUIRED");
        this.auditPort = required(auditPort, "CHANGE_PACKAGE_AUDIT_PORT_REQUIRED");
        this.skillSignalPort = required(skillSignalPort, "CHANGE_PACKAGE_SKILL_SIGNAL_PORT_REQUIRED");
        this.transactionPort = required(transactionPort, "CHANGE_PACKAGE_TRANSACTION_PORT_REQUIRED");
    }

    public ChangePackageApprovalOutcome submitReview(ChangePackageCommands.SubmitReview command) {
        ChangePackageCommands.SubmitReview requiredCommand = required(command, "CHANGE_PACKAGE_SUBMIT_REVIEW_COMMAND_REQUIRED");
        return transactionPort.required(() -> submitReviewInTransaction(requiredCommand));
    }

    public ChangePackageApprovalOutcome approve(ChangePackageCommands.Approve command) {
        ChangePackageCommands.Approve requiredCommand = required(command, "CHANGE_PACKAGE_APPROVE_COMMAND_REQUIRED");
        return transactionPort.required(() -> approveInTransaction(requiredCommand));
    }

    public ChangePackageApprovalOutcome reject(ChangePackageCommands.Reject command) {
        ChangePackageCommands.Reject requiredCommand = required(command, "CHANGE_PACKAGE_REJECT_COMMAND_REQUIRED");
        return transactionPort.required(() -> rejectInTransaction(requiredCommand));
    }

    private ChangePackageApprovalOutcome submitReviewInTransaction(ChangePackageCommands.SubmitReview command) {
        ChangePackageCurrent current = current(command.packageId());
        ChangePackageStatus nextStatus = ChangePackageAggregate.rehydrate(current.pointer()).submitReview().status();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packageId", current.packageId());
        payload.put("version", current.version());
        payload.put("packageHash", current.packageHash());
        payload.put("comment", command.request().comment());
        if (!pointers().compareAndSetStatus(current.pointer(), nextStatus)) {
            throw new IllegalStateException("ChangePackage 状态已漂移，提交审核 CAS 失败：" + current.packageId());
        }
        appendEvent(current.packageId(), "PACKAGE_SUBMITTED_REVIEW", command.actor(),
                "ChangePackage 已提交人工审核", payload);
        auditPort.record(current.projectId(), "submit-review", current.packageId(), current, payload);
        return new ChangePackageApprovalOutcome(current.packageId(), nextStatus,
                current.version(), current.packageHash(), 0, 0);
    }

    private ChangePackageApprovalOutcome approveInTransaction(ChangePackageCommands.Approve command) {
        ChangePackageCurrent current = current(command.packageId());
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(current.pointer());
        aggregate.requireApprovable(command.version(), command.packageHash());
        ChangePackageVersion targetVersion = version(current.packageId(), command.version());
        String frozenHash = targetVersion.packageHash();
        if (!current.packageHash().equals(frozenHash)) {
            throw new IllegalStateException("ChangePackage 当前版本 hash 与版本快照不一致，拒绝审批");
        }
        if (!frozenHash.equals(command.packageHash())) {
            throw new IllegalStateException("ChangePackage packageHash 与已审批版本不一致，拒绝审批");
        }

        proofPort.verifyBeforeApprove(current, targetVersion);
        ChangePackageApprovalAssessment assessment = ASSESSMENT_FACTORY.assess(
                current, targetVersion, command.actor(), command.approvalContext().actorContext());
        saveApproval(current, targetVersion, assessment, command);
        int approvedCount = approvals().countDistinctApproved(
                current.packageId(), targetVersion.version(), targetVersion.packageHash());
        int requiredApprovals = assessment.requirement().requiredApprovals();

        if (approvedCount < requiredApprovals) {
            Map<String, Object> payload = approvalRecordedPayload(
                    current, targetVersion, assessment, command.actor(), requiredApprovals, approvedCount);
            appendEvent(current.packageId(), "PACKAGE_APPROVAL_RECORDED", command.actor(),
                    "ChangePackage 已记录审批意见，等待更多审批人确认", payload);
            auditPort.record(current.projectId(), "approval-recorded", current.packageId(), current, payload);
            return new ChangePackageApprovalOutcome(current.packageId(), current.status(),
                    current.version(), current.packageHash(), requiredApprovals, approvedCount);
        }

        aggregate.approve(command.version(), command.packageHash());
        if (!pointers().compareAndSetApproved(current.pointer(), targetVersion.snapshot(), command.actor())) {
            throw new IllegalStateException("ChangePackage 状态或版本已漂移，审批 CAS 失败：" + current.packageId());
        }
        Map<String, Object> payload = approvedPayload(targetVersion, assessment, command.actor());
        appendEvent(current.packageId(), "PACKAGE_APPROVED", command.actor(),
                "ChangePackage 已审批通过 v" + targetVersion.version(), payload);
        auditPort.record(current.projectId(), "approve", current.packageId(), current, payload);
        skillSignalPort.recordAccepted(current, targetVersion, command.actor());
        skillSignalPort.reconcileApprovedOutcome(targetVersion);
        return new ChangePackageApprovalOutcome(current.packageId(), ChangePackageStatus.APPROVED,
                targetVersion.version(), targetVersion.packageHash(), requiredApprovals, approvedCount);
    }

    private ChangePackageApprovalOutcome rejectInTransaction(ChangePackageCommands.Reject command) {
        ChangePackageCurrent current = current(command.packageId());
        ChangePackageStatus nextStatus = ChangePackageAggregate.rehydrate(current.pointer()).reject().status();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", command.request().reason());
        payload.put("comment", command.request().comment());
        if (!pointers().compareAndSetStatus(current.pointer(), nextStatus)) {
            throw new IllegalStateException("ChangePackage 状态已漂移，驳回 CAS 失败：" + current.packageId());
        }
        appendEvent(current.packageId(), "PACKAGE_REJECTED", command.actor(),
                "ChangePackage 被驳回，等待 Preparation Agent 修订", payload);
        auditPort.record(current.projectId(), "reject", current.packageId(), current, payload);
        return new ChangePackageApprovalOutcome(current.packageId(), nextStatus,
                current.version(), current.packageHash(), 0, 0);
    }

    private void saveApproval(ChangePackageCurrent current,
                              ChangePackageVersion targetVersion,
                              ChangePackageApprovalAssessment assessment,
                              ChangePackageCommands.Approve command) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actorScope", assessment.actorScope());
        metadata.put("adminConfirmation", assessment.adminConfirmation());
        metadata.put("riskLevel", assessment.riskLevel());
        metadata.put("targetEnvironment", assessment.snapshot().currentState()
                .value(ChangePackageCurrentField.TARGET_ENVIRONMENT));
        metadata.put("comment", command.approvalContext().comment());
        approvals().saveDecision(new ChangePackageApproval(
                "cpa-" + UUID.randomUUID(),
                current.packageId(),
                current.projectId(),
                targetVersion.version(),
                targetVersion.packageHash(),
                assessment.riskLevel(),
                command.actor(),
                assessment.actorScope(),
                "APPROVED",
                assessment.adminConfirmation(),
                metadata));
    }

    private Map<String, Object> approvalRecordedPayload(ChangePackageCurrent current,
                                                        ChangePackageVersion targetVersion,
                                                        ChangePackageApprovalAssessment assessment,
                                                        String actor,
                                                        int requiredApprovals,
                                                        int approvedCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packageId", current.packageId());
        payload.put("version", targetVersion.version());
        payload.put("packageHash", targetVersion.packageHash());
        payload.put("riskLevel", assessment.riskLevel());
        payload.put("requiredApprovals", requiredApprovals);
        payload.put("approvedCount", approvedCount);
        payload.put("approvedBy", text(actor));
        return payload;
    }

    private Map<String, Object> approvedPayload(ChangePackageVersion targetVersion,
                                                ChangePackageApprovalAssessment assessment,
                                                String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packageId", targetVersion.packageId());
        payload.put("approvedVersion", targetVersion.version());
        payload.put("approvedPackageHash", targetVersion.packageHash());
        payload.put("approvedSnapshot", assessment.snapshot().toMap());
        payload.put("approvedBy", text(actor));
        return payload;
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
                text(actor),
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

    private IChangePackageApprovalRepository approvals() {
        if (!approvalRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_STORE_UNAVAILABLE");
        return approvalRepository;
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
