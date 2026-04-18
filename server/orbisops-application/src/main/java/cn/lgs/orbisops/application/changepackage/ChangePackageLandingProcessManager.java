package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageAggregate;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingCompletion;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageLandingPlanFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Long-running Landing process manager. Database facts are durable before an
 * external operation is dispatched; the external runtime is deliberately not
 * wrapped in one database transaction.
 */
public final class ChangePackageLandingProcessManager {

    private static final ChangePackageLandingPlanFactory PLAN_FACTORY = new ChangePackageLandingPlanFactory();
    private static final Set<ChangePackageStatus> RECOVERABLE_PACKAGE_STATES = Set.of(
            ChangePackageStatus.APPROVED,
            ChangePackageStatus.LANDING_RUNNING,
            ChangePackageStatus.LANDING_FAILED);

    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageVersionRepository versionRepository;
    private final IChangePackagePointerRepository pointerRepository;
    private final IChangePackageEventRepository eventRepository;
    private final ChangePackageLandingGatePort gatePort;
    private final ChangePackageLandingRunPort runPort;
    private final ChangePackageLandingJournalPort journalPort;
    private final ChangePackageLandingLockPort lockPort;
    private final ChangePackageLandingRuntimePort runtimePort;
    private final ChangePackageAuditPort auditPort;
    private final ChangePackageLandingSignalPort signalPort;
    private final ToolExecutionIdempotencyPort toolExecutionLedger;

    public ChangePackageLandingProcessManager(IChangePackageCurrentRepository currentRepository,
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
        this.currentRepository = required(currentRepository, "CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.versionRepository = required(versionRepository, "CHANGE_PACKAGE_VERSION_REPOSITORY_REQUIRED");
        this.pointerRepository = required(pointerRepository, "CHANGE_PACKAGE_POINTER_REPOSITORY_REQUIRED");
        this.eventRepository = required(eventRepository, "CHANGE_PACKAGE_EVENT_REPOSITORY_REQUIRED");
        this.gatePort = required(gatePort, "CHANGE_PACKAGE_LANDING_GATE_PORT_REQUIRED");
        this.runPort = required(runPort, "CHANGE_PACKAGE_LANDING_RUN_PORT_REQUIRED");
        this.journalPort = required(journalPort, "CHANGE_PACKAGE_LANDING_JOURNAL_PORT_REQUIRED");
        this.lockPort = required(lockPort, "CHANGE_PACKAGE_LANDING_LOCK_PORT_REQUIRED");
        this.runtimePort = required(runtimePort, "CHANGE_PACKAGE_LANDING_RUNTIME_PORT_REQUIRED");
        this.auditPort = required(auditPort, "CHANGE_PACKAGE_AUDIT_PORT_REQUIRED");
        this.signalPort = required(signalPort, "CHANGE_PACKAGE_LANDING_SIGNAL_PORT_REQUIRED");
        this.toolExecutionLedger = required(toolExecutionLedger, "TOOL_EXECUTION_IDEMPOTENCY_PORT_REQUIRED");
    }

    public ChangePackageLandingOutcome land(ChangePackageCommands.Land command) {
        ChangePackageCommands.Land requiredCommand = required(command, "CHANGE_PACKAGE_LAND_COMMAND_REQUIRED");
        gatePort.requireLandingEnabled();
        if (!journalPort.available()) {
            throw new IllegalStateException("LANDING_OPERATION_JOURNAL_UNAVAILABLE");
        }
        ChangePackageCurrent current = current(requiredCommand.packageId());
        ChangePackageLandingRequest request = ChangePackageLandingRequest.from(
                current.pointer(), requiredCommand.request());
        ChangePackageVersion approvedVersion = version(
                current.packageId(), current.pointer().approvedVersion());
        ChangePackageLandingPlan plan = PLAN_FACTORY.create(current, approvedVersion);
        gatePort.requireLandingEnabled(plan);
        String idempotencyKey = request.effectiveIdempotencyKey(current.packageId());
        Optional<ChangePackageLandingRun> existing = runPort.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty() && !request.idempotencyKey().isBlank()) {
            // Compatibility lookup for LandingRun rows created before custom keys were
            // namespaced to the frozen package pointer. A foreign-scope legacy collision
            // is ignored rather than being treated as an idempotent replay.
            existing = runPort.findByIdempotencyKey(request.idempotencyKey())
                    .filter(run -> sameLandingScope(current, run));
        }
        if (existing.isPresent()) {
            return existingOutcome(current, existing.get(), idempotencyKey, requiredCommand.actor());
        }

        requireSafeFailedLandingRetry(current, request);
        ChangePackagePointer runningPointer = ChangePackageAggregate.rehydrate(current.pointer()).startLanding();
        String landingRunId = "lr-" + UUID.randomUUID();
        String leaseToken = "lease-" + UUID.randomUUID();
        runPort.start(landingRunId, idempotencyKey, current,
                plan.approvedVersion(), plan.approvedPackageHash(), leaseToken, requiredCommand.actor());

        boolean journalInitialized = false;
        boolean packageTransitioned = false;
        List<String> locks = List.of();
        Map<String, Object> result;
        ChangePackageLandingRuntimeResult runtimeResult;
        try {
            journalPort.initialize(landingRunId, plan);
            journalInitialized = true;
            locks = lockPort.acquire(landingRunId, leaseToken, current, plan, requiredCommand.actor());
            if (!pointers().compareAndSetLandingStarted(current.pointer(), landingRunId)) {
                throw new IllegalStateException("LANDING_PACKAGE_CAS_CONFLICT");
            }
            packageTransitioned = true;
            appendEvent(current.packageId(), "LANDING_STARTED", requiredCommand.actor(),
                    "Approved LandingRuntime 已取得 ChangePackage 执行权", Map.of(
                            "landingRunId", landingRunId,
                            "approvedVersion", plan.approvedVersion(),
                            "approvedPackageHash", plan.approvedPackageHash()));
            runtimeResult = runtimePort.execute(
                    current, approvedVersion, plan, request, landingRunId, requiredCommand.actor());
            result = runtimeResult.mutablePayload();
            result.put("landingRunId", landingRunId);
            result.put("idempotencyKey", idempotencyKey);
            journalPort.completeFromRuntimeResult(landingRunId, plan, runtimeResult);
            runPort.complete(landingRunId, runtimeResult.runStatus(), result);
        } catch (RuntimeException error) {
            ChangePackageLandingRuntimeResult failureResult = failedResult(
                    landingRunId, idempotencyKey, error);
            Map<String, Object> failed = failureResult.mutablePayload();
            if (journalInitialized) {
                journalPort.completeFromRuntimeResult(landingRunId, plan, failureResult);
            }
            runPort.complete(landingRunId, failureResult.runStatus(), failed);
            if (packageTransitioned) {
                finishLanding(current, runningPointer, ChangePackageStatus.LANDING_FAILED,
                        landingRunId, failed);
            }
            appendEvent(current.packageId(), failureResult.eventType(), requiredCommand.actor(),
                    failureResult.summary(), failed);
            auditPort.record(current.projectId(), "landing-failed", current.packageId(), current, failed);
            throw error;
        } finally {
            lockPort.release(locks, leaseToken);
        }

        ChangePackageStatus status = runtimeResult.status();
        finishLanding(current, runningPointer, status, landingRunId, result);
        appendEvent(current.packageId(), runtimeResult.eventType(), requiredCommand.actor(),
                runtimeResult.summary(), result);
        auditPort.record(current.projectId(), "land", current.packageId(), current, result);
        signalPort.recordOutcome(current, approvedVersion, landingRunId, status.name(), result);
        return new ChangePackageLandingOutcome(current.packageId(), landingRunId,
                idempotencyKey, status, false, result);
    }

    /** Closes a crashed run only after persisted execution facts are safe to reconcile. */
    public boolean reconcile(String landingRunId, String actor) {
        ChangePackageLandingRun run = runPort.find(requiredText(landingRunId,
                        "CHANGE_PACKAGE_LANDING_RUN_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalStateException("LandingRun 不存在，无法完成对账：" + landingRunId));
        if (toolExecutionLedger.hasUnresolvedSideEffect(run.projectId(), landingRunId)) {
            return false;
        }
        List<LandingOperationFact> operationFacts = journalPort.operationFacts(landingRunId);
        if (operationFacts.isEmpty() || operationFacts.stream().anyMatch(LandingOperationFact::unknown)) return false;

        if (run.status() == ChangePackageLandingRunStatus.NEEDS_REPLAN) {
            Map<String, Object> result = new LinkedHashMap<>(run.result());
            result.put("landingRunId", landingRunId);
            result.put("status", ChangePackageStatus.NEEDS_REPLAN.name());
            result.putIfAbsent("reasonCode", "LANDING_RECONCILED_NEEDS_REPLAN");
            result.put("recovered", true);
            result.put("operations", operationFacts.stream().map(LandingOperationFact::payload).toList());
            finishRecoveredLanding(run, ChangePackageStatus.NEEDS_REPLAN, result, false, actor,
                    "LandingRun 已权威判定原审批方案需要重新规划；恢复器仅修复 durable pointer，不重放 operation。");
            return true;
        }

        boolean succeeded = !operationFacts.isEmpty()
                && operationFacts.stream().allMatch(LandingOperationFact::succeeded);
        Map<String, Object> verification = Map.of();
        boolean verificationFailed = false;
        if (succeeded) {
            ChangePackageCurrent current = current(run.packageId());
            if (!sameLandingScope(current, run) || !run.runId().equals(current.landingRunId())) {
                throw new IllegalStateException("LANDING_RECOVERY_APPROVED_POINTER_DRIFT");
            }
            try {
                verification = verifyCompleted(current, run, operationFacts,
                        requiredText(runPort.executionActor(landingRunId), "LANDING_VERIFICATION_OWNER_REQUIRED"));
            } catch (RuntimeException unavailable) {
                // Keep the recovery open. A write acknowledgement is not proof of postconditions,
                // and an unavailable verifier must never promote the durable package pointer.
                return false;
            }
            if (verification == null || verification.isEmpty()) return false;
            succeeded = Boolean.TRUE.equals(verification.get("passed"));
            verificationFailed = !succeeded;
        }
        boolean provenUnexecuted = operationFacts.stream().allMatch(fact ->
                fact.factStatus() == LandingOperationFact.FactStatus.NONE
                        && fact.executionStatus() == LandingOperationFact.ExecutionStatus.PENDING);
        // Recovery must not invent plan invalidity. If authoritative facts prove that no operation
        // was dispatched, the approved plan is still valid and a fresh LandingRun may retry under
        // the existing approval. NEEDS_REPLAN is reserved for an explicit runtime plan-invalid signal.
        ChangePackageStatus packageStatus = succeeded
                ? ChangePackageStatus.LANDED
                : ChangePackageStatus.LANDING_FAILED;
        String reasonCode = succeeded
                ? "LANDING_RECONCILED_SUCCEEDED"
                : verificationFailed ? "LANDING_INDEPENDENT_VERIFICATION_FAILED"
                : provenUnexecuted
                        ? "LANDING_RECONCILED_UNEXECUTED"
                        : "LANDING_RECONCILED_FAILED";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("landingRunId", landingRunId);
        result.put("status", packageStatus.name());
        result.put("reasonCode", reasonCode);
        result.put("recovered", true);
        result.put("executedProductionAction", succeeded
                || operationFacts.stream().anyMatch(LandingOperationFact::completed));
        result.put("operations", operationFacts.stream().map(LandingOperationFact::payload).toList());
        if (!verification.isEmpty()) result.put("independentVerification", verification);
        ChangePackageLandingRunStatus runStatus = succeeded
                ? ChangePackageLandingRunStatus.SUCCEEDED
                : ChangePackageLandingRunStatus.FAILED;
        runPort.complete(landingRunId, runStatus, result);

        finishRecoveredLanding(run, packageStatus, result, succeeded, actor,
                succeeded ? "Landing operation 已通过权威对账和 post-check 完成。"
                        : provenUnexecuted
                                ? "权威对账确认旧 LandingRun 未派发 operation；按执行失败收口，允许新 LandingRun 安全重试。"
                                : "Landing recovery 已按已知失败事实收口，方案本身未被自动判定失效。");
        return true;
    }

    /** Explicit re-observation; a newly passing failed run may reconcile without replaying a write. */
    public Map<String, Object> verifyLanding(String packageId, String actor) {
        ChangePackageCurrent current = current(packageId);
        if (!Set.of(ChangePackageStatus.LANDED, ChangePackageStatus.LANDING_FAILED).contains(current.status())) {
            throw new IllegalStateException("LANDING_VERIFICATION_REQUIRES_TERMINAL_RUN");
        }
        ChangePackageLandingRun run = runPort.find(current.landingRunId())
                .orElseThrow(() -> new IllegalStateException("LANDING_RUN_NOT_FOUND"));
        if (!sameLandingScope(current, run) || !run.runId().equals(current.landingRunId())
                || toolExecutionLedger.hasUnresolvedSideEffect(current.projectId(), run.runId())) {
            throw new IllegalStateException("LANDING_VERIFICATION_UNRESOLVED_IDENTITY");
        }
        List<LandingOperationFact> facts = journalPort.operationFacts(run.runId());
        Map<String, Object> proof = verifyCompleted(current, run, facts, actor);
        appendEvent(packageId, "LANDING_POSTCHECK_OBSERVED", actor,
                Boolean.TRUE.equals(proof.get("passed")) ? "已重新读取目标并通过已批准的后置检查。"
                        : "目标未通过已批准的后置检查；保留失败证据，不重放写操作。", proof);
        if (current.status() == ChangePackageStatus.LANDING_FAILED
                && run.status() == ChangePackageLandingRunStatus.FAILED
                && Boolean.TRUE.equals(proof.get("passed"))
                && !facts.isEmpty() && facts.stream().allMatch(LandingOperationFact::succeeded)) {
            // Re-read under the original execution owner and existing tuple/fact/side-effect fences.
            // The explicit observation above and every previous failure remain separate audit events.
            reconcile(run.runId(), actor);
            Map<String, Object> observed = new LinkedHashMap<>(proof);
            ChangePackageCurrent after = current(packageId);
            observed.put("reconciled", after.status() == ChangePackageStatus.LANDED
                    && sameLandingScope(after, run) && run.runId().equals(after.landingRunId()));
            observed.put("landingStatus", after.status().name());
            return observed;
        }
        return proof;
    }

    private Map<String, Object> verifyCompleted(ChangePackageCurrent current, ChangePackageLandingRun run,
                                                List<LandingOperationFact> facts, String actor) {
        ChangePackageVersion approved = version(current.packageId(), current.pointer().approvedVersion());
        ChangePackageLandingPlan plan = PLAN_FACTORY.create(current, approved);
        return runtimePort.verifyCompletedOperations(current, approved, plan, run.runId(), actor, facts);
    }

    /**
     * Repairs a durable projection gap where LandingRun is terminal but its package pointer is still
     * LANDING_RUNNING. It never dispatches or replays an operation.
     */
    public Map<String, Object> recoverStrandedTerminalRuns(int limit, String actor) {
        return reconcileCandidates(
                runPort.findStrandedTerminalRuns(Math.max(1, Math.min(limit, 100))),
                actor);
    }

    /**
     * Repairs the inverse projection race: the runtime failed closed before authoritative ToolExecution
     * completion reached the frozen operation journal. Repository selection only returns failed runs whose
     * frozen operations are now all COMPLETED/SUCCEEDED; reconcile() rechecks side-effect uncertainty and
     * durable facts before changing the package to LANDED. No operation is dispatched or replayed here.
     */
    public Map<String, Object> recoverLateSuccessfulCompletions(int limit, String actor) {
        return reconcileCandidates(
                runPort.findLateCompletedFailedRuns(Math.max(1, Math.min(limit, 100))),
                actor);
    }

    private Map<String, Object> reconcileCandidates(
            List<ChangePackageLandingRun> candidates,
            String actor) {
        int reconciled = 0;
        int unresolved = 0;
        int failed = 0;
        for (ChangePackageLandingRun candidate : candidates) {
            try {
                if (reconcile(candidate.runId(), actor)) reconciled++;
                else unresolved++;
            } catch (RuntimeException ignored) {
                // Keep durable facts untouched for a later pass. One broken row must not prevent
                // independent LandingRuns from being examined.
                failed++;
            }
        }
        return Map.of(
                "scanned", candidates.size(),
                "reconciled", reconciled,
                "unresolved", unresolved,
                "failed", failed);
    }

    private void finishRecoveredLanding(ChangePackageLandingRun run,
                                        ChangePackageStatus status,
                                        Map<String, Object> result,
                                        boolean succeeded,
                                        String actor,
                                        String summary) {
        ChangePackageCurrent current = current(run.packageId());
        if (!RECOVERABLE_PACKAGE_STATES.contains(current.status())) {
            throw new IllegalStateException("LANDING_RECOVERY_PACKAGE_STATE_DRIFT：packageId="
                    + current.packageId() + " status=" + current.status());
        }
        if (!sameLandingScope(current, run) || !run.runId().equals(current.landingRunId())) {
            throw new IllegalStateException("LANDING_RECOVERY_APPROVED_POINTER_DRIFT：packageId=" + current.packageId());
        }
        ChangePackagePointer next = ChangePackageAggregate.rehydrate(current.pointer())
                .reconcileLanding(status);
        ChangePackageLandingCompletion completion = new ChangePackageLandingCompletion(
                next.status(), run.runId(), result, succeeded ? Map.of() : result);
        if (!pointers().compareAndSetLandingResult(current.pointer(), completion)) {
            throw new IllegalStateException("LANDING_RECOVERY_PACKAGE_STATE_DRIFT：packageId=" + current.packageId());
        }
        appendEvent(current.packageId(), succeeded ? "LANDING_RECONCILED" : "LANDING_RECONCILIATION_CLOSED",
                actor, summary, result);
        auditPort.record(current.projectId(), "landing-reconcile", current.packageId(), current, result);
    }

    private ChangePackageLandingOutcome existingOutcome(ChangePackageCurrent current,
                                                         ChangePackageLandingRun existing,
                                                         String idempotencyKey,
                                                         String actor) {
        if (!sameLandingScope(current, existing)) {
            throw new SecurityException("LANDING_IDEMPOTENCY_SCOPE_CONFLICT");
        }
        if (existing.succeeded()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("landingRunId", existing.runId());
            payload.put("idempotencyKey", idempotencyKey);
            payload.put("status", existing.status().name());
            appendEvent(current.packageId(), "LANDING_IDEMPOTENT_REPLAY", actor,
                    "重复 landing 请求命中已成功的 idempotencyKey，未重复执行生产动作。", payload);
            auditPort.record(current.projectId(), "landing-idempotent-replay",
                    current.packageId(), current, payload);
            return new ChangePackageLandingOutcome(current.packageId(), existing.runId(),
                    idempotencyKey, ChangePackageStatus.LANDED, true, payload);
        }
        if (existing.status() == ChangePackageLandingRunStatus.RUNNING
                && !existing.leaseExpired()) {
            throw new IllegalStateException("ChangePackage landing 正在执行中，拒绝重复生产写操作：" + existing.runId());
        }
        throw new IllegalStateException("LANDING_RETRY_REQUIRES_RECONCILIATION：已有 LandingRun 状态="
                + existing.status() + "，禁止自动重放可能已产生副作用的 operation");
    }

    private void requireSafeFailedLandingRetry(
            ChangePackageCurrent current,
            ChangePackageLandingRequest request) {
        if (current == null || current.status() != ChangePackageStatus.LANDING_FAILED) return;
        if (request == null || request.idempotencyKey().isBlank()) {
            throw new IllegalStateException("LANDING_RETRY_IDEMPOTENCY_KEY_REQUIRED");
        }
        String previousRunId = requiredText(current.landingRunId(), "LANDING_RETRY_PREVIOUS_RUN_REQUIRED");
        ChangePackageLandingRun previousRun = runPort.find(previousRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "LANDING_RETRY_PREVIOUS_RUN_NOT_FOUND:" + previousRunId));
        if (!sameLandingScope(current, previousRun)
                || previousRun.status() != ChangePackageLandingRunStatus.FAILED) {
            throw new IllegalStateException("LANDING_RETRY_PREVIOUS_RUN_STATE_INVALID:" + previousRunId);
        }
        if (toolExecutionLedger.hasUnresolvedSideEffect(current.projectId(), previousRunId)) {
            throw new IllegalStateException("LANDING_RETRY_REQUIRES_RECONCILIATION:UNRESOLVED_SIDE_EFFECT");
        }
        List<LandingOperationFact> facts = journalPort.operationFacts(previousRunId);
        boolean provenUnexecuted = !facts.isEmpty()
                && facts.stream().allMatch(fact ->
                        fact.factStatus() == LandingOperationFact.FactStatus.NONE
                                && fact.executionStatus() == LandingOperationFact.ExecutionStatus.PENDING);
        if (!provenUnexecuted) {
            throw new IllegalStateException("LANDING_RETRY_REQUIRES_RECONCILIATION:OPERATIONS_NOT_PROVEN_PENDING");
        }
    }

    private boolean sameLandingScope(
            ChangePackageCurrent current,
            ChangePackageLandingRun run) {
        return current != null
                && run != null
                && current.packageId().equals(run.packageId())
                && current.projectId().equals(run.projectId())
                && current.pointer().approvedVersion() == run.approvedVersion()
                && current.pointer().approvedPackageHash().equals(run.approvedPackageHash());
    }

    private void finishLanding(ChangePackageCurrent current,
                               ChangePackagePointer runningPointer,
                               ChangePackageStatus status,
                               String landingRunId,
                               Map<String, Object> result) {
        ChangePackagePointer next = ChangePackageAggregate.rehydrate(runningPointer).finishLanding(status);
        Map<String, Object> failureSummary = status == ChangePackageStatus.LANDED ? Map.of() : result;
        ChangePackageLandingCompletion completion = new ChangePackageLandingCompletion(
                next.status(), landingRunId, result, failureSummary);
        if (!pointers().compareAndSetLandingResult(runningPointer, completion)) {
            throw new IllegalStateException("LANDING_STATUS_CAS_CONFLICT：落地结果未覆盖已变化的 ChangePackage");
        }
    }

    private ChangePackageLandingRuntimeResult failedResult(
            String runId,
            String idempotencyKey,
            RuntimeException error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", text(error.getMessage()));
        payload.put("landingRunId", runId);
        payload.put("idempotencyKey", idempotencyKey);
        return new ChangePackageLandingRuntimeResult(
                ChangePackageStatus.LANDING_FAILED,
                "LANDING_FAILED",
                "LANDING_RUNTIME_EXCEPTION",
                "LandingRuntime 执行失败，未将异常伪装为成功：" + text(error.getMessage()),
                false,
                payload);
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
                text(summary),
                payload == null ? Map.of() : payload,
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
