package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogMutation;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.model.SkillStatus;
import cn.lgs.orbisops.domain.skill.model.SkillUpdateMode;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Application transaction boundary for ordinary Skill catalog create and mutation commands. */
public final class SkillCatalogMutationUseCase {

    private static final String GLOBAL = "GLOBAL";
    private static final String PROJECT = "PROJECT";
    private static final Set<String> ORIGINS = Set.of("MANUAL", "EVOLVED", "IMPORTED");

    private final ISkillCatalogRepository catalogRepository;
    private final ISkillPackageRepository packageRepository;
    private final SkillTransactionPort transactionPort;
    private final SkillProjectValidationPort projectValidationPort;
    private final SkillPackageManifest.Limits packageLimits;
    private final SkillGovernancePolicy governancePolicy = new SkillGovernancePolicy();

    public SkillCatalogMutationUseCase(ISkillCatalogRepository catalogRepository,
                                       ISkillPackageRepository packageRepository,
                                       SkillTransactionPort transactionPort,
                                       SkillProjectValidationPort projectValidationPort,
                                       SkillPackageManifest.Limits packageLimits) {
        if (catalogRepository == null) throw new IllegalArgumentException("SKILL_CATALOG_REPOSITORY_REQUIRED");
        if (packageRepository == null) throw new IllegalArgumentException("SKILL_PACKAGE_REPOSITORY_REQUIRED");
        if (transactionPort == null) throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        if (projectValidationPort == null) {
            throw new IllegalArgumentException("SKILL_PROJECT_VALIDATION_PORT_REQUIRED");
        }
        if (packageLimits == null) throw new IllegalArgumentException("SKILL_PACKAGE_LIMITS_REQUIRED");
        this.catalogRepository = catalogRepository;
        this.packageRepository = packageRepository;
        this.transactionPort = transactionPort;
        this.projectValidationPort = projectValidationPort;
        this.packageLimits = packageLimits;
    }

    public SkillCatalogWriteOutcome createGlobal(Map<String, Object> request, String actor) {
        return create(GLOBAL, "", request, actor);
    }

    public SkillCatalogWriteOutcome createProject(String projectId,
                                                  Map<String, Object> request,
                                                  String actor) {
        return create(PROJECT, required(projectId, "SKILL_PROJECT_ID_REQUIRED"), request, actor);
    }

    public SkillCatalogWriteOutcome updateGlobal(String skillId,
                                                 Map<String, Object> currentSnapshot,
                                                 Map<String, Object> request,
                                                 String actor) {
        return update(GLOBAL, "", skillId, currentSnapshot, request, actor);
    }

    public SkillCatalogWriteOutcome updateProject(String projectId,
                                                  String skillId,
                                                  Map<String, Object> currentSnapshot,
                                                  Map<String, Object> request,
                                                  String actor) {
        return update(PROJECT, required(projectId, "SKILL_PROJECT_ID_REQUIRED"),
                skillId, currentSnapshot, request, actor);
    }

    private SkillCatalogWriteOutcome create(String scope,
                                            String projectId,
                                            Map<String, Object> request,
                                            String actor) {
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> command = copy(request);
        String skillId = SkillCatalogFingerprint.normalizeId(
                firstText(command, "", "skillId", "name"));
        if (skillId.isBlank()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        return transactionPort.required(() -> createRequired(
                scope, projectId, skillId, command, operator, 1, 0, ""));
    }

    private SkillCatalogWriteOutcome update(String scope,
                                            String projectId,
                                            String skillId,
                                            Map<String, Object> currentSnapshot,
                                            Map<String, Object> request,
                                            String actor) {
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        String normalizedSkillId = SkillCatalogFingerprint.normalizeId(
                required(skillId, "SKILL_ID_REQUIRED"));
        if (normalizedSkillId.isBlank()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        Map<String, Object> snapshot = copy(currentSnapshot);
        if (snapshot.isEmpty()) throw new IllegalArgumentException("SKILL_CURRENT_REQUIRED");
        Map<String, Object> mutation = copy(request);
        int expectedVersion = positiveInt(first(
                mutation.get("baseVersion"), snapshot.get("currentVersion"), snapshot.get("version")),
                "SKILL_BASE_VERSION_REQUIRED");
        String expectedHash = firstText(mutation, firstText(snapshot, "",
                "currentSkillHash", "skillHash"), "baseSkillHash");
        return transactionPort.required(() -> updateRequired(
                scope, projectId, normalizedSkillId, snapshot, mutation,
                expectedVersion, expectedHash, operator));
    }

    private SkillCatalogWriteOutcome createRequired(String scope,
                                                    String projectId,
                                                    String skillId,
                                                    Map<String, Object> command,
                                                    String actor,
                                                    int version,
                                                    int baseVersion,
                                                    String baseSkillHash) {
        requireProject(scope, projectId);
        requireStores();
        String name = firstText(command, skillId, "name", "skillName");
        String description = text(command.get("description"), "");
        String content = content(command, "");
        if (content.isBlank()) throw new IllegalArgumentException("SKILL_CONTENT_REQUIRED");
        String status = status(command.get("status"), SkillStatus.ENABLED).name();
        String updateMode = updateMode(first(command.get("updateMode"), command.get("update_mode")),
                SkillUpdateMode.MANUAL_ONLY).name();
        String origin = origin(command.get("origin"));
        boolean autoUpdateEnabled = bool(first(command.get("autoUpdateEnabled"),
                command.get("auto_update_enabled")), true);
        boolean autoMergeEnabled = bool(first(command.get("autoMergeEnabled"),
                command.get("auto_merge_enabled")), true);
        String sourceGlobalSkillId = text(command.get("sourceGlobalSkillId"), "");
        Map<String, Object> governanceView = copy(command);
        governanceView.putIfAbsent("status", status);
        governanceView.putIfAbsent("updateMode", updateMode);
        governanceView.putIfAbsent("frozenBy", actor);
        SkillGovernanceState governanceState = governancePolicy.governanceState(governanceView);
        status = governanceState.legacyStatusProjection();
        updateMode = governanceState.legacyUpdateModeProjection();
        String frozenReason = governanceState.lock().reason();
        String frozenBy = governanceState.lock().actor();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime frozenAt = governanceState.lock().lockedAt();
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.create(
                scope, projectId, skillId, name, description, version, content,
                command, packageLimits);
        String skillHash = SkillCatalogFingerprint.sha256(
                scope, projectId, skillId, name, description, content, version,
                status, updateMode, autoUpdateEnabled, autoMergeEnabled);
        SkillCatalogEntry entry = new SkillCatalogEntry(
                0L, skillId, projectId, name, scope, sourceGlobalSkillId,
                description, content, version, status, actor, null, null,
                origin, updateMode, autoUpdateEnabled, autoMergeEnabled,
                "EVOLVED".equals(origin) ? now : null,
                frozenReason, frozenBy, frozenAt,
                skillHash, version, skillHash, version, descriptor.packageHash(),
                descriptor.manifestJson(), CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()),
                governanceState);
        if (!catalogRepository.insertIfAbsent(entry)) {
            throw new IllegalArgumentException("Skill 已存在：" + skillId);
        }
        SkillPackageVersion published = version(
                scope, projectId, skillId, version, skillHash, baseVersion, baseSkillHash,
                content, command, actor, descriptor,
                baseVersion == 0 ? "MANUAL_CREATE" : "MANUAL_MATERIALIZE");
        packageRepository.appendVersion(published, artifacts(descriptor));
        return new SkillCatalogWriteOutcome(true, null, published);
    }

    private SkillCatalogWriteOutcome updateRequired(String scope,
                                                    String projectId,
                                                    String skillId,
                                                    Map<String, Object> snapshot,
                                                    Map<String, Object> mutation,
                                                    int expectedVersion,
                                                    String expectedHash,
                                                    String actor) {
        requireProject(scope, projectId);
        requireStores();
        Optional<SkillCatalogEntry> stored = catalogRepository.find(scope, projectId, skillId, true);
        if (stored.isEmpty()) {
            Map<String, Object> materialized = merge(snapshot, mutation);
            materialized.put("skillId", skillId);
            materialized.put("createBy", actor);
            return createRequired(scope, projectId, skillId, materialized, actor,
                    expectedVersion + 1, expectedVersion, expectedHash);
        }

        SkillCatalogEntry current = stored.get();
        if (current.currentVersion() != expectedVersion
                || !current.currentSkillHash().equals(expectedHash == null ? "" : expectedHash)) {
            throw new IllegalStateException("SKILL_CURRENT_POINTER_CONFLICT：" + skillId);
        }
        governancePolicy.requireOrdinaryMutation(current.governanceState());
        Map<String, Object> desired = merge(currentState(current), mutation);
        int nextVersion = expectedVersion + 1;
        String name = firstText(desired, skillId, "name", "skillName");
        String sourceGlobalSkillId = firstText(desired, current.sourceGlobalSkillId(), "sourceGlobalSkillId");
        String description = text(desired.get("description"), current.description());
        String content = content(desired, current.content());
        if (content.isBlank()) throw new IllegalArgumentException("SKILL_CONTENT_REQUIRED");
        requireNoCriticalGovernanceMutation(mutation);
        String status = status(desired.get("status"), status(current.status(), SkillStatus.ENABLED)).name();
        String updateMode = updateMode(first(desired.get("updateMode"), desired.get("update_mode")),
                updateMode(current.updateMode(), SkillUpdateMode.MANUAL_ONLY)).name();
        SkillGovernanceState legacyDerived = SkillGovernanceState.fromLegacy(
                status, updateMode, "", "", null);
        SkillGovernanceState governanceState = new SkillGovernanceState(
                legacyDerived.lifecycleStatus(), legacyDerived.mutationMode(), legacyDerived.executionMode(),
                current.governanceState().bindingMode(), current.governanceState().lock(), false);
        status = governanceState.legacyStatusProjection();
        updateMode = governanceState.legacyUpdateModeProjection();
        String origin = origin(first(desired.get("origin"), current.origin()));
        boolean autoUpdateEnabled = bool(first(desired.get("autoUpdateEnabled"),
                desired.get("auto_update_enabled")), current.autoUpdateEnabled());
        boolean autoMergeEnabled = bool(first(desired.get("autoMergeEnabled"),
                desired.get("auto_merge_enabled")), current.autoMergeEnabled());
        String frozenReason = governanceState.lock().reason();
        String frozenBy = governanceState.lock().actor();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime frozenAt = governanceState.lock().lockedAt();
        List<SkillArtifact> currentArtifacts = packageRepository.findArtifacts(current.key());
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.evolve(
                scope, projectId, skillId, name, description, nextVersion, content,
                mutation, currentArtifacts, current.packageManifestJson(), packageLimits);
        String nextHash = SkillCatalogFingerprint.sha256(
                scope, projectId, skillId, name, description, content, nextVersion,
                status, updateMode, autoUpdateEnabled, autoMergeEnabled);
        SkillCatalogMutation command = new SkillCatalogMutation(
                scope, projectId, skillId, expectedVersion, expectedHash, nextVersion, nextHash,
                name, sourceGlobalSkillId, description, content, status, origin, updateMode,
                autoUpdateEnabled, autoMergeEnabled,
                "EVOLVED".equals(origin) ? now : current.lastEvolvedAt(),
                frozenReason, frozenBy, frozenAt,
                descriptor.packageHash(), descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()),
                governanceState);
        if (!catalogRepository.compareAndSetMutation(command)) {
            throw new IllegalStateException("SKILL_CURRENT_POINTER_CONFLICT：" + skillId);
        }
        SkillPackageVersion published = version(
                scope, projectId, skillId, nextVersion, nextHash,
                expectedVersion, expectedHash, content, mutation, actor, descriptor,
                "MANUAL_UPDATE");
        packageRepository.appendVersion(published, artifacts(descriptor));
        return new SkillCatalogWriteOutcome(false, current, published);
    }

    private SkillPackageVersion version(String scope,
                                        String projectId,
                                        String skillId,
                                        int version,
                                        String skillHash,
                                        int baseVersion,
                                        String baseSkillHash,
                                        String content,
                                        Map<String, Object> command,
                                        String actor,
                                        SkillPackageManifest.Descriptor descriptor,
                                        String defaultPublishMode) {
        return new SkillPackageVersion(
                0L,
                new SkillPackageKey(scope, projectId, skillId, version),
                skillHash,
                Math.max(0, baseVersion),
                text(baseSkillHash, ""),
                text(command.get("sourceRunId"), ""),
                text(command.get("sourceSessionId"), ""),
                text(command.get("evolutionJobId"), ""),
                text(command.get("publishMode"), defaultPublishMode),
                content,
                text(command.get("sourceType"), baseVersion == 0 ? origin(command.get("origin")) : "MANUAL"),
                actor,
                text(command.get("changeSummary"), baseVersion == 0 ? "create skill" : "update skill"),
                descriptor.packageHash(),
                descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()),
                descriptor.entrypoint(),
                descriptor.artifactCount(),
                descriptor.packageSize(),
                null);
    }

    private List<SkillArtifact> artifacts(SkillPackageManifest.Descriptor descriptor) {
        return descriptor.artifacts().values().stream()
                .map(artifact -> new SkillArtifact(
                        artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                        artifact.contentHash(), artifact.sizeBytes(), artifact.content()))
                .toList();
    }

    private void requireProject(String scope, String projectId) {
        if (PROJECT.equals(scope)) projectValidationPort.requireExisting(projectId);
    }

    private void requireStores() {
        if (!catalogRepository.available()) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
        if (!packageRepository.available()) throw new IllegalStateException("SKILL_PACKAGE_STORE_UNAVAILABLE");
    }

    private Map<String, Object> currentState(SkillCatalogEntry current) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("skillId", current.skillId());
        state.put("projectId", current.projectId());
        state.put("name", current.name());
        state.put("sourceGlobalSkillId", current.sourceGlobalSkillId());
        state.put("description", current.description());
        state.put("content", current.content());
        state.put("status", current.status());
        state.put("origin", current.origin());
        state.put("updateMode", current.updateMode());
        state.put("lifecycleStatus", current.governanceState().lifecycleStatus().name());
        state.put("mutationMode", current.governanceState().mutationMode().name());
        state.put("executionMode", current.governanceState().executionMode().name());
        state.put("bindingMode", current.governanceState().bindingMode().name());
        state.put("lockType", current.governanceState().lock().type().name());
        state.put("lockReason", current.governanceState().lock().reason());
        state.put("lockActor", current.governanceState().lock().actor());
        state.put("lockApprovalId", current.governanceState().lock().approvalId());
        state.put("legacyFrozenClassificationRequired",
                current.governanceState().legacyFrozenClassificationRequired());
        state.put("autoUpdateEnabled", current.autoUpdateEnabled());
        state.put("autoMergeEnabled", current.autoMergeEnabled());
        state.put("frozenReason", current.frozenReason());
        return state;
    }

    private void requireNoCriticalGovernanceMutation(Map<String, Object> mutation) {
        Map<String, Object> command = copy(mutation);
        for (String key : Set.of(
                "lifecycleStatus", "mutationMode", "executionMode", "bindingMode",
                "lockType", "lockReason", "lockActor", "lockApprovalId",
                "legacyFrozenClassificationRequired")) {
            if (command.containsKey(key)) {
                throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:" + key);
            }
        }
        String targetStatus = text(command.get("status"), "").toUpperCase(Locale.ROOT);
        if ("FROZEN".equals(targetStatus)) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:status");
        }
        String targetMode = text(first(command.get("updateMode"), command.get("update_mode")), "")
                .toUpperCase(Locale.ROOT);
        if (Set.of(SkillMutationMode.LOCKED.name(), SkillMutationMode.SEALED.name(), "FROZEN")
                .contains(targetMode)) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:updateMode");
        }
    }

    private Map<String, Object> merge(Map<String, Object> current, Map<String, Object> mutation) {
        Map<String, Object> result = copy(current);
        result.putAll(copy(mutation));
        return result;
    }

    private Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private String content(Map<String, Object> source, String fallback) {
        if (source.containsKey("content")) return rawText(source.get("content"));
        if (source.containsKey("markdown")) return rawText(source.get("markdown"));
        return fallback == null ? "" : fallback;
    }

    private SkillStatus status(Object value, SkillStatus fallback) {
        String normalized = text(value, "");
        return normalized.isBlank() ? fallback : SkillStatus.require(normalized);
    }

    private SkillUpdateMode updateMode(Object value, SkillUpdateMode fallback) {
        String normalized = text(value, "");
        return normalized.isBlank() ? fallback : SkillUpdateMode.require(normalized);
    }

    private String origin(Object value) {
        String normalized = text(value, "MANUAL").toUpperCase(Locale.ROOT);
        return ORIGINS.contains(normalized) ? normalized : "MANUAL";
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) return fallback;
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "enabled".equalsIgnoreCase(normalized);
    }

    private int positiveInt(Object value, String reasonCode) {
        int number;
        if (value instanceof Number numeric) number = numeric.intValue();
        else {
            try {
                number = Integer.parseInt(text(value, "0"));
            } catch (Exception ignored) {
                number = 0;
            }
        }
        if (number <= 0) throw new IllegalArgumentException(reasonCode);
        return number;
    }

    private Object first(Object... values) {
        for (Object value : values) {
            if (value != null && !text(value, "").isBlank()) return value;
        }
        return null;
    }

    private String firstText(Map<String, Object> source, String fallback, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key), "");
            if (!value.isBlank()) return value;
        }
        return fallback == null ? "" : fallback;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String rawText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
