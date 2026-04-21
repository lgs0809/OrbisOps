package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.util.List;

public final class SkillRollbackUseCase {

    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_PROJECT = "PROJECT";

    private final ISkillCatalogRepository catalogRepository;
    private final ISkillPackageRepository packageRepository;
    private final SkillTransactionPort transactionPort;
    private final SkillPackageManifest.Limits packageLimits;
    private final SkillGovernancePolicy governancePolicy;
    private final SkillRecoverySafetyPort recoverySafety;

    public SkillRollbackUseCase(ISkillCatalogRepository catalogRepository,
                                ISkillPackageRepository packageRepository,
                                SkillTransactionPort transactionPort,
                                SkillPackageManifest.Limits packageLimits) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits,
                new SkillGovernancePolicy(), (release, baseline) -> false);
    }

    SkillRollbackUseCase(ISkillCatalogRepository catalogRepository,
                         ISkillPackageRepository packageRepository,
                         SkillTransactionPort transactionPort,
                         SkillPackageManifest.Limits packageLimits,
                         SkillGovernancePolicy governancePolicy) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits, governancePolicy,
                (release, baseline) -> false);
    }

    public SkillRollbackUseCase(ISkillCatalogRepository catalogRepository,
                                ISkillPackageRepository packageRepository,
                                SkillTransactionPort transactionPort,
                                SkillPackageManifest.Limits packageLimits,
                                SkillRecoverySafetyPort recoverySafety) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits, new SkillGovernancePolicy(), recoverySafety);
    }

    private SkillRollbackUseCase(ISkillCatalogRepository catalogRepository,
                         ISkillPackageRepository packageRepository,
                         SkillTransactionPort transactionPort,
                         SkillPackageManifest.Limits packageLimits,
                         SkillGovernancePolicy governancePolicy,
                         SkillRecoverySafetyPort recoverySafety) {
        if (catalogRepository == null) throw new IllegalArgumentException("SKILL_CATALOG_REPOSITORY_REQUIRED");
        if (packageRepository == null) throw new IllegalArgumentException("SKILL_PACKAGE_REPOSITORY_REQUIRED");
        if (transactionPort == null) throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        if (packageLimits == null) throw new IllegalArgumentException("SKILL_PACKAGE_LIMITS_REQUIRED");
        if (governancePolicy == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_POLICY_REQUIRED");
        this.catalogRepository = catalogRepository;
        this.packageRepository = packageRepository;
        this.transactionPort = transactionPort;
        this.packageLimits = packageLimits;
        this.governancePolicy = governancePolicy;
        this.recoverySafety = java.util.Objects.requireNonNull(recoverySafety);
    }

    /** Internal release guard: never overwrite a newer user version or enable an unverified baseline. */
    public SkillReleaseRecoveryOutcome recoverRelease(cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot release) {
        if (release == null || release.releasedVersion() <= 0 || release.releasedSkillHash().isBlank())
            throw new IllegalArgumentException("SKILL_RELEASE_RECOVERY_IDENTITY_REQUIRED");
        return transactionPort.required(() -> {
            requireStores();
            var current = catalogRepository.find(SCOPE_PROJECT, release.projectId(), release.targetSkillId(), true)
                    .orElseThrow(() -> new IllegalStateException("SKILL_RELEASE_TARGET_MISSING"));
            if (current.currentVersion() != release.releasedVersion()
                    || !current.currentSkillHash().equals(release.releasedSkillHash()))
                throw new IllegalStateException("SKILL_RELEASE_RECOVERY_CURRENT_VERSION_CHANGED");
            var baseline = release.baselineVersion() > 0 ? packageRepository.findVersion(new SkillPackageKey(
                    SCOPE_PROJECT, release.projectId(), release.targetSkillId(), release.baselineVersion())) : java.util.Optional.<SkillPackageVersion>empty();
            if (current.governanceState().activeAtUse() && current.governanceState().ordinaryMutationAllowed()
                    && baseline.isPresent() && baseline.get().skillHash().equals(release.baselineSkillHash())
                    && recoverySafety.safeToRestore(release, baseline.get())) {
                var restored = rollbackRequired(SCOPE_PROJECT, release.projectId(), release.targetSkillId(),
                        release.baselineVersion(), "SYSTEM_SKILL_RELEASE_GUARD", current).publishedVersion();
                return new SkillReleaseRecoveryOutcome(true, restored.key().version(), restored.skillHash(), "VERIFIED_BASELINE_RESTORED");
            }
            var before = current.governanceState();
            var after = new cn.lgs.orbisops.domain.skill.model.SkillGovernanceState(before.lifecycleStatus(),
                    before.mutationMode(), cn.lgs.orbisops.domain.skill.model.SkillExecutionMode.QUARANTINED,
                    before.bindingMode(), before.lock(), before.legacyFrozenClassificationRequired());
            if (!catalogRepository.compareAndSetGovernance(new cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate(
                    SCOPE_PROJECT, release.projectId(), release.targetSkillId(), release.releasedVersion(), release.releasedSkillHash(), before, after)))
                throw new IllegalStateException("SKILL_RELEASE_QUARANTINE_CONFLICT");
            return new SkillReleaseRecoveryOutcome(false, current.currentVersion(), current.currentSkillHash(),
                    "METHOD_QUARANTINED_NO_VERIFIED_COMPATIBLE_BASELINE");
        });
    }

    public SkillRollbackOutcome rollbackGlobal(String skillId, int sourceVersion, String actor) {
        return rollback(SCOPE_GLOBAL, "", skillId, sourceVersion, actor);
    }

    public SkillRollbackOutcome rollbackProject(String projectId,
                                                 String skillId,
                                                 int sourceVersion,
                                                 String actor) {
        return rollback(SCOPE_PROJECT, required(projectId, "SKILL_PROJECT_ID_REQUIRED"),
                skillId, sourceVersion, actor);
    }

    private SkillRollbackOutcome rollback(String scope,
                                           String projectId,
                                           String skillId,
                                           int sourceVersion,
                                           String actor) {
        String normalizedSkillId = SkillCatalogFingerprint.normalizeId(
                required(skillId, "SKILL_ID_REQUIRED"));
        if (normalizedSkillId.isBlank()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        if (sourceVersion <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        return transactionPort.required(() -> rollbackRequired(
                scope, projectId, normalizedSkillId, sourceVersion, operator));
    }

    private SkillRollbackOutcome rollbackRequired(String scope,
                                                   String projectId,
                                                   String skillId,
                                                   int sourceVersion,
                                                   String actor) {
        return rollbackRequired(scope, projectId, skillId, sourceVersion, actor, null);
    }

    private SkillRollbackOutcome rollbackRequired(String scope, String projectId, String skillId,
                                                   int sourceVersion, String actor, SkillCatalogEntry expected) {
        requireStores();
        SkillCatalogEntry current = catalogRepository.find(scope, projectId, skillId, true)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + skillId));
        if (expected != null && (current.currentVersion() != expected.currentVersion()
                || !current.currentSkillHash().equals(expected.currentSkillHash())
                || !current.governanceState().equals(expected.governanceState())))
            throw new IllegalStateException("SKILL_RELEASE_RECOVERY_CURRENT_VERSION_CHANGED");
        governancePolicy.requireRollback(current, sourceVersion);

        SkillPackageKey sourceKey = new SkillPackageKey(scope, projectId, skillId, sourceVersion);
        SkillPackageVersion source = packageRepository.findVersion(sourceKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Skill 版本不存在：" + skillId + "@" + sourceVersion));
        List<SkillArtifact> sourceArtifacts = packageRepository.findArtifacts(sourceKey);

        int baseVersion = current.currentVersion();
        String baseHash = current.currentSkillHash();
        int nextVersion = baseVersion + 1;
        String name = current.name().isBlank() ? skillId : current.name();
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.rebuild(
                scope, projectId, skillId, name, current.description(), nextVersion,
                source.content(), sourceArtifacts, source.manifestJson(), packageLimits);
        String nextHash = SkillCatalogFingerprint.sha256(
                scope, projectId, skillId, name, current.description(), source.content(), nextVersion,
                current.status(), current.updateMode(), current.autoUpdateEnabled(), current.autoMergeEnabled());

        boolean advanced = catalogRepository.compareAndSetCurrent(new SkillCurrentPointerUpdate(
                scope, projectId, skillId, baseVersion, baseHash, nextVersion, nextHash,
                name, current.description(), source.content(), "MANUAL", descriptor.packageHash(),
                descriptor.manifestJson(), CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes())));
        if (!advanced) throw new IllegalStateException("SKILL_CURRENT_POINTER_CONFLICT：" + skillId);

        SkillPackageVersion published = new SkillPackageVersion(
                0L,
                new SkillPackageKey(scope, projectId, skillId, nextVersion),
                nextHash,
                baseVersion,
                baseHash,
                "",
                "",
                "",
                actor.startsWith("SYSTEM_") ? "SYSTEM_ROLLBACK" : "MANUAL_ROLLBACK",
                source.content(),
                "ROLLBACK",
                actor,
                "rollback to version " + sourceVersion,
                descriptor.packageHash(),
                descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()),
                descriptor.entrypoint(),
                descriptor.artifactCount(),
                descriptor.packageSize(),
                null);
        List<SkillArtifact> publishedArtifacts = descriptor.artifacts().values().stream()
                .map(artifact -> new SkillArtifact(
                        artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                        artifact.contentHash(), artifact.sizeBytes(), artifact.content()))
                .toList();
        packageRepository.appendVersion(published, publishedArtifacts);
        return new SkillRollbackOutcome(current, published);
    }

    private void requireStores() {
        if (!catalogRepository.available()) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
        if (!packageRepository.available()) throw new IllegalStateException("SKILL_PACKAGE_STORE_UNAVAILABLE");
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
