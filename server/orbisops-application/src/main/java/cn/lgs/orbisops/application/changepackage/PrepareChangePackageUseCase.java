package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageAggregate;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageSnapshotFactory;
import java.util.Map;
import java.util.UUID;

/** Application-owned creation and revision orchestration. */
public final class PrepareChangePackageUseCase {

    private static final ChangePackageSnapshotFactory SNAPSHOT_FACTORY = new ChangePackageSnapshotFactory();

    private final ChangePackagePreparationPlanPort planPort;
    private final ChangePackagePreparationProofPort proofPort;
    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageVersionRepository versionRepository;
    private final IChangePackagePointerRepository pointerRepository;
    private final IChangePackageEventRepository eventRepository;
    private final ChangePackageAuditPort auditPort;
    private final ChangePackageTransactionPort transactionPort;
    private final ChangePackageQueryPort queryPort;

    public PrepareChangePackageUseCase(ChangePackagePreparationPlanPort planPort,
                                       ChangePackagePreparationProofPort proofPort,
                                       IChangePackageCurrentRepository currentRepository,
                                       IChangePackageVersionRepository versionRepository,
                                       IChangePackagePointerRepository pointerRepository,
                                       IChangePackageEventRepository eventRepository,
                                       ChangePackageAuditPort auditPort,
                                       ChangePackageTransactionPort transactionPort,
                                       ChangePackageQueryPort queryPort) {
        this.planPort = required(planPort, "CHANGE_PACKAGE_PREPARATION_PLAN_PORT_REQUIRED");
        this.proofPort = required(proofPort, "CHANGE_PACKAGE_PREPARATION_PROOF_PORT_REQUIRED");
        this.currentRepository = required(currentRepository, "CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.versionRepository = required(versionRepository, "CHANGE_PACKAGE_VERSION_REPOSITORY_REQUIRED");
        this.pointerRepository = required(pointerRepository, "CHANGE_PACKAGE_POINTER_REPOSITORY_REQUIRED");
        this.eventRepository = required(eventRepository, "CHANGE_PACKAGE_EVENT_REPOSITORY_REQUIRED");
        this.auditPort = required(auditPort, "CHANGE_PACKAGE_AUDIT_PORT_REQUIRED");
        this.transactionPort = required(transactionPort, "CHANGE_PACKAGE_TRANSACTION_PORT_REQUIRED");
        this.queryPort = required(queryPort, "CHANGE_PACKAGE_QUERY_PORT_REQUIRED");
    }

    public Map<String, Object> prepare(ChangePackageCommands.Prepare command) {
        ChangePackageCommands.Prepare requiredCommand = required(command, "CHANGE_PACKAGE_PREPARE_COMMAND_REQUIRED");
        ChangePackagePreparationPlan plan = planPort.prepare(
                requiredCommand.request(),
                requiredCommand.actor());
        return transactionPort.required(() -> create(plan, requiredCommand.actor()));
    }

    public Map<String, Object> prepareForSession(ChangePackageCommands.PrepareForSession command) {
        ChangePackageCommands.PrepareForSession requiredCommand = required(
                command, "CHANGE_PACKAGE_PREPARE_SESSION_COMMAND_REQUIRED");
        ChangePackagePreparationPlan plan = planPort.prepareForSession(
                requiredCommand.sessionId(),
                requiredCommand.request(),
                requiredCommand.actor());
        return transactionPort.required(() -> create(plan, requiredCommand.actor()));
    }

    public Map<String, Object> revise(ChangePackageCommands.Revise command) {
        ChangePackageCommands.Revise requiredCommand = required(command, "CHANGE_PACKAGE_REVISE_COMMAND_REQUIRED");
        ChangePackageRevisionPlan revision = planPort.revise(
                requiredCommand.packageId(),
                requiredCommand.request(),
                requiredCommand.actor());
        return transactionPort.required(() -> reviseInTransaction(
                requiredCommand.packageId(), revision, requiredCommand.actor()));
    }


    private Map<String, Object> create(
            ChangePackagePreparationPlan plan,
            String actor) {
        ChangePackagePreparationPlan requiredPlan = required(
                plan, "CHANGE_PACKAGE_PREPARATION_PLAN_REQUIRED");
        Map<String, Object> safe = requiredPlan.snapshotInput();
        String projectId = requiredPlan.projectId();
        String packageId = firstNonBlank(
                requiredPlan.requestedPackageId(),
                "cp-" + UUID.randomUUID());
        int version = 1;
        ChangePackageSnapshot snapshot = SNAPSHOT_FACTORY.create(
                packageId, version, safe, actor, requiredPlan.trustedProofs());
        Map<String, Object> values = snapshot.toMap();
        ChangePackageStatus status = snapshot.status();
        ChangePackagePointer pointer = new ChangePackagePointer(
                packageId, status, version, snapshot.packageHash(), 0, "");
        currents().insert(ChangePackageCurrent.draft(
                pointer,
                snapshot.sessionId(),
                snapshot.incidentId(),
                projectId,
                snapshot.preparationAgentId(),
                snapshot.preparationAgentVersion(),
                snapshot.packageType(),
                snapshot.currentState(),
                actor));
        versions().append(new ChangePackageVersion(
                0L, packageId, version, snapshot.packageHash(), status.name(),
                snapshot, "initial", actor, null));
        proofPort.recordExecutionProofs(packageId, version, snapshot.packageHash(), projectId, snapshot, actor);
        appendEvent(packageId, "PACKAGE_CREATED", actor, "ChangePackage 已创建", values);
        auditPort.record(projectId, "create", packageId, null, values);
        return queryPort.detail(packageId);
    }

    private Map<String, Object> reviseInTransaction(
            String packageId,
            ChangePackageRevisionPlan revision,
            String actor) {
        ChangePackageRevisionPlan requiredRevision = required(
                revision, "CHANGE_PACKAGE_REVISION_PLAN_REQUIRED");
        ChangePackageCurrent current = current(packageId);
        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(current.pointer());
        aggregate.requireRevision();
        ChangePackageVersion sourceVersion = version(packageId, current.version());
        if (!current.packageHash().equals(sourceVersion.packageHash())) {
            throw new IllegalStateException("CHANGE_PACKAGE_REVISION_SOURCE_HASH_MISMATCH");
        }
        int nextVersion = current.version() + 1;
        Map<String, Object> merged = sourceVersion.snapshot().toMap();
        merged.put("packageId", current.packageId());
        merged.put("sessionId", current.sessionId());
        merged.put("incidentId", current.incidentId());
        merged.put("projectId", current.projectId());
        merged.put("preparationAgentId", current.preparationAgentId());
        merged.put("preparationAgentVersion", current.preparationAgentVersion());
        merged.put("packageType", current.packageType().name());
        current.state().values().forEach((field, value) -> merged.put(field.snapshotKey(), value));
        merged.putAll(requiredRevision.changes());
        merged.put("version", nextVersion);
        merged.put("status", ChangePackageStatus.REVISING.name());
        merged.remove("approvedVersion");
        merged.remove("approved_version");
        merged.remove("approvedPackageHash");
        merged.remove("approved_package_hash");
        merged.remove("approveBy");
        merged.remove("approve_by");
        merged.remove("approvedAt");
        merged.remove("approved_at");
        ChangePackageSnapshot snapshot = SNAPSHOT_FACTORY.create(packageId, nextVersion, merged, actor);
        ChangePackagePointer nextPointer = aggregate.revise(nextVersion, snapshot.packageHash());
        if (!pointers().compareAndSetVersion(current.pointer(), nextPointer, snapshot.currentState())) {
            throw new IllegalStateException(
                    "CHANGE_PACKAGE_REVISION_CONFLICT：状态、版本或 packageHash 已变化，请刷新后重试");
        }
        String summary = requiredRevision.changeSummary();
        versions().append(new ChangePackageVersion(
                0L, packageId, nextVersion, snapshot.packageHash(), ChangePackageStatus.REVISING.name(),
                snapshot, summary, actor, null));
        appendEvent(packageId, "PACKAGE_REVISED", actor,
                "ChangePackage 已生成新版本 v" + nextVersion, snapshot.toMap());
        auditPort.record(current.projectId(), "revise", packageId, current, snapshot.toMap());
        return queryPort.detail(packageId);
    }

    private void appendEvent(String packageId,
                             String eventType,
                             String actor,
                             String summary,
                             Map<String, Object> payload) {
        events().append(new ChangePackageEvent(
                0L, "cpe-" + UUID.randomUUID(), packageId, eventType,
                text(actor), summary, payload, null));
    }

    private ChangePackageCurrent current(String packageId) {
        return currents().find(packageId)
                .orElseThrow(() -> new IllegalArgumentException("ChangePackage 不存在：" + packageId));
    }

    private ChangePackageVersion version(String packageId, int version) {
        return versions().find(packageId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ChangePackage 版本不存在：" + packageId + "@" + version));
    }

    private IChangePackageCurrentRepository currents() {
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

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private String requiredText(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }
}
