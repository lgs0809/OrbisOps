package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillBindingMode;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleStatus;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillLockType;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import cn.lgs.orbisops.domain.skill.model.SkillStatus;
import cn.lgs.orbisops.domain.skill.model.SkillUpdateMode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Domain policy for orthogonal Skill lifecycle, mutation, execution and binding governance. */
public final class SkillGovernancePolicy {

    private static final Set<String> CRITICAL_GOVERNANCE_KEYS = Set.of(
            "lifecycleStatus", "mutationMode", "executionMode", "bindingMode",
            "lockType", "lockReason", "lockActor", "lockApprovalId", "lockAt",
            "legacyFrozenClassificationRequired");

    public Map<String, Object> normalizeCreate(Map<String, Object> request) {
        Map<String, Object> result = copy(request);
        SkillStatus legacyStatus = status(result.get("status"), SkillStatus.ENABLED);
        SkillUpdateMode legacyUpdateMode = updateMode(result.get("updateMode"), SkillUpdateMode.MANUAL_ONLY);
        result.put("status", legacyStatus.name());
        result.put("updateMode", legacyUpdateMode.name());
        putGovernance(result, SkillGovernanceState.fromLegacy(
                legacyStatus.name(), legacyUpdateMode.name(),
                text(result.get("frozenReason")), text(result.get("frozenBy")), null));
        return result;
    }

    public Map<String, Object> normalizeUpdate(Map<String, Object> current, Map<String, Object> request) {
        requireOrdinaryMutation(governanceState(current));
        Map<String, Object> result = copy(request);
        for (String key : CRITICAL_GOVERNANCE_KEYS) {
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:" + key);
            }
        }
        if (result.containsKey("status")) {
            result.put("status", status(result.get("status"), currentStatus(current)).name());
        }
        if (result.containsKey("updateMode")) {
            SkillUpdateMode target = updateMode(result.get("updateMode"), currentMode(current));
            if (target == SkillUpdateMode.FROZEN
                    || target == SkillUpdateMode.LOCKED
                    || target == SkillUpdateMode.SEALED) {
                throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:updateMode");
            }
            result.put("updateMode", target.name());
        }
        return result;
    }

    public String transitionStatus(Map<String, Object> current, String targetStatus) {
        requireOrdinaryMutation(governanceState(current));
        SkillStatus target = SkillStatus.require(targetStatus);
        if (target == SkillStatus.FROZEN) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:status");
        }
        return target.name();
    }

    /** Compatibility API limited to AUTO/MANUAL_ONLY; LOCK/SEAL use dedicated commands. */
    public Map<String, Object> normalizeUpdateMode(Map<String, Object> current, Map<String, Object> request) {
        requireOrdinaryMutation(governanceState(current));
        SkillUpdateMode target = updateMode(
                first(request == null ? null : request.get("updateMode"),
                        request == null ? null : request.get("mode")), currentMode(current));
        if (target == SkillUpdateMode.FROZEN
                || target == SkillUpdateMode.LOCKED
                || target == SkillUpdateMode.SEALED) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:updateMode");
        }
        Map<String, Object> result = copy(request);
        result.put("updateMode", target.name());
        return result;
    }

    public void requireEvolutionPublish(Map<String, Object> current,
                                        int baseVersion,
                                        String baseSkillHash) {
        SkillGovernanceState state = governanceState(current);
        if (!state.autoPublishAllowed()) {
            throw new IllegalStateException(autoPublishReason(state));
        }
        int currentVersion = integer(first(current.get("currentVersion"), current.get("version")), 0);
        String currentHash = text(first(current.get("currentSkillHash"), current.get("skillHash")));
        requireEvolutionBase(currentVersion, currentHash, baseVersion, baseSkillHash);
    }

    public void requireEvolutionPublish(SkillCatalogEntry current,
                                        int baseVersion,
                                        String baseSkillHash) {
        if (current == null) throw new IllegalArgumentException("SKILL_CURRENT_REQUIRED");
        if (!current.governanceState().autoPublishAllowed()) {
            throw new IllegalStateException(autoPublishReason(current.governanceState()));
        }
        requireEvolutionBase(current.currentVersion(), current.currentSkillHash(), baseVersion, baseSkillHash);
    }

    public void requireRollback(Map<String, Object> current, int version) {
        SkillGovernanceState state = governanceState(current);
        if (state.legacyFrozenClassificationRequired()) {
            throw new IllegalStateException("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED");
        }
        requireOrdinaryMutation(state);
        if (version <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
    }

    public void requireRollback(SkillCatalogEntry current, int version) {
        if (current == null) throw new IllegalArgumentException("SKILL_CURRENT_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        if (current.governanceState().legacyFrozenClassificationRequired()) {
            throw new IllegalStateException("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED");
        }
        requireOrdinaryMutation(current.governanceState());
    }

    public void requireEmergencyRestore(SkillCatalogEntry current, int version) {
        if (current == null) throw new IllegalArgumentException("SKILL_CURRENT_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        SkillGovernanceState state = current.governanceState();
        if (state.mutationMode() == SkillMutationMode.SEALED) {
            throw new IllegalStateException("SKILL_EMERGENCY_RESTORE_FORBIDDEN_WHILE_SEALED");
        }
        if (state.mutationMode() != SkillMutationMode.LOCKED
                && state.executionMode() != SkillExecutionMode.QUARANTINED) {
            throw new IllegalStateException("SKILL_EMERGENCY_RESTORE_STATE_REQUIRED");
        }
    }

    public void requireOrdinaryMutation(SkillGovernanceState state) {
        if (state == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_STATE_REQUIRED");
        if (!state.ordinaryMutationAllowed()) {
            throw new IllegalStateException(state.mutationMode() == SkillMutationMode.SEALED
                    ? "SKILL_MUTATION_FORBIDDEN_WHILE_SEALED"
                    : "SKILL_MUTATION_FORBIDDEN_WHILE_LOCKED");
        }
    }

    public String autoPublishSkipReason(SkillCatalogEntry current) {
        return current == null ? "" : current.autoPublishSkipReason();
    }

    public boolean canAutoUpdate(Map<String, Object> current) {
        return governanceState(current).autoPublishAllowed()
                && bool(first(current.get("autoUpdateEnabled"), current.get("auto_update_enabled")));
    }

    /** Legacy compatibility name: true now means mutation locked/sealed or unresolved legacy freeze. */
    public boolean frozen(Map<String, Object> current) {
        SkillGovernanceState state = governanceState(current);
        return state.mutationMode() == SkillMutationMode.LOCKED
                || state.mutationMode() == SkillMutationMode.SEALED
                || state.legacyFrozenClassificationRequired();
    }

    public SkillGovernanceState governanceState(Map<String, Object> current) {
        if (current == null || current.isEmpty()) throw new IllegalArgumentException("SKILL_CURRENT_REQUIRED");
        boolean hasOrthogonalState = current.containsKey("lifecycleStatus")
                || current.containsKey("mutationMode")
                || current.containsKey("executionMode")
                || current.containsKey("bindingMode");
        if (!hasOrthogonalState) {
            return SkillGovernanceState.fromLegacy(
                    text(first(current.get("status"), current.get("lifecycle"))),
                    text(first(current.get("updateMode"), current.get("update_mode"))),
                    text(first(current.get("frozenReason"), current.get("frozen_reason"))),
                    text(first(current.get("frozenBy"), current.get("frozen_by"))),
                    null);
        }
        SkillLifecycleStatus lifecycle = lifecycle(first(
                current.get("lifecycleStatus"), current.get("lifecycle_status")));
        SkillMutationMode mutation = mutation(first(
                current.get("mutationMode"), current.get("mutation_mode")));
        SkillExecutionMode execution = execution(first(
                current.get("executionMode"), current.get("execution_mode")));
        SkillBindingMode binding = binding(first(
                current.get("bindingMode"), current.get("binding_mode")));
        SkillLockType lockType = SkillLockType.require(text(first(
                current.get("lockType"), current.get("lock_type"))));
        SkillLock lock = lockType == SkillLockType.NONE
                ? SkillLock.none()
                : new SkillLock(
                        lockType,
                        fallback(first(current.get("lockReason"), current.get("frozenReason")), "governance lock"),
                        fallback(first(current.get("lockActor"), current.get("frozenBy")), "SYSTEM_GOVERNANCE"),
                        text(current.get("lockApprovalId")),
                        LocalDateTime.of(1970, 1, 1, 0, 0));
        return new SkillGovernanceState(
                lifecycle, mutation, execution, binding, lock,
                bool(current.get("legacyFrozenClassificationRequired")));
    }

    private String autoPublishReason(SkillGovernanceState state) {
        if (state.legacyFrozenClassificationRequired()) return "SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED";
        if (state.mutationMode() == SkillMutationMode.LOCKED) return "SKILL_EVOLUTION_FORBIDDEN_WHILE_LOCKED";
        if (state.mutationMode() == SkillMutationMode.SEALED) return "SKILL_EVOLUTION_FORBIDDEN_WHILE_SEALED";
        return "SKILL_EVOLUTION_NOT_AUTO_PUBLISHABLE";
    }

    private void putGovernance(Map<String, Object> target, SkillGovernanceState state) {
        target.put("lifecycleStatus", state.lifecycleStatus().name());
        target.put("mutationMode", state.mutationMode().name());
        target.put("executionMode", state.executionMode().name());
        target.put("bindingMode", state.bindingMode().name());
        target.put("lockType", state.lock().type().name());
        target.put("lockReason", state.lock().reason());
        target.put("lockActor", state.lock().actor());
        target.put("lockApprovalId", state.lock().approvalId());
        target.put("legacyFrozenClassificationRequired", state.legacyFrozenClassificationRequired());
    }

    private SkillLifecycleStatus lifecycle(Object value) {
        String normalized = text(value);
        return normalized.isBlank() ? SkillLifecycleStatus.ACTIVE : SkillLifecycleStatus.require(normalized);
    }

    private SkillMutationMode mutation(Object value) {
        String normalized = text(value);
        return normalized.isBlank() ? SkillMutationMode.MANUAL_ONLY : SkillMutationMode.require(normalized);
    }

    private SkillExecutionMode execution(Object value) {
        String normalized = text(value);
        return normalized.isBlank() ? SkillExecutionMode.ENABLED : SkillExecutionMode.require(normalized);
    }

    private SkillBindingMode binding(Object value) {
        String normalized = text(value);
        return normalized.isBlank() ? SkillBindingMode.FLOATING : SkillBindingMode.require(normalized);
    }

    private SkillStatus currentStatus(Map<String, Object> current) {
        return status(first(current.get("status"), current.get("lifecycle")), SkillStatus.ENABLED);
    }

    private SkillUpdateMode currentMode(Map<String, Object> current) {
        return updateMode(first(current.get("updateMode"), current.get("update_mode")), SkillUpdateMode.MANUAL_ONLY);
    }

    private SkillStatus status(Object value, SkillStatus fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : SkillStatus.require(normalized);
    }

    private SkillUpdateMode updateMode(Object value, SkillUpdateMode fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : SkillUpdateMode.require(normalized);
    }

    private void requireEvolutionBase(int currentVersion,
                                      String currentHash,
                                      int baseVersion,
                                      String baseSkillHash) {
        if (baseVersion <= 0 || baseVersion != currentVersion) {
            throw new IllegalStateException("SKILL_EVOLUTION_BASE_VERSION_CONFLICT");
        }
        if (text(baseSkillHash).isBlank() || !text(baseSkillHash).equals(text(currentHash))) {
            throw new IllegalStateException("SKILL_EVOLUTION_BASE_HASH_CONFLICT");
        }
    }

    private Map<String, Object> copy(Map<String, Object> input) {
        return input == null ? new LinkedHashMap<>() : new LinkedHashMap<>(input);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Object first(Object left, Object right) {
        return left == null || text(left).isBlank() ? right : left;
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
