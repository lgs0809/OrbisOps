package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Emergency restore of a known-good immutable package into SHADOW_ONLY execution. */
public final class RestoreSkillUseCase {

    private final ISkillCatalogRepository catalogRepository;
    private final ISkillPackageRepository packageRepository;
    private final SkillTransactionPort transactionPort;
    private final SkillPackageManifest.Limits packageLimits;
    private final SkillGovernanceAuthorizationPort authorizationPort;
    private final SkillGovernanceAuditPort auditPort;
    private final SkillGovernancePolicy governancePolicy;
    private final Clock clock;

    public RestoreSkillUseCase(ISkillCatalogRepository catalogRepository,
                               ISkillPackageRepository packageRepository,
                               SkillTransactionPort transactionPort,
                               SkillPackageManifest.Limits packageLimits,
                               SkillGovernanceAuthorizationPort authorizationPort,
                               SkillGovernanceAuditPort auditPort) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits,
                authorizationPort, auditPort, new SkillGovernancePolicy(), Clock.systemUTC());
    }

    RestoreSkillUseCase(ISkillCatalogRepository catalogRepository,
                        ISkillPackageRepository packageRepository,
                        SkillTransactionPort transactionPort,
                        SkillPackageManifest.Limits packageLimits,
                        SkillGovernanceAuthorizationPort authorizationPort,
                        SkillGovernanceAuditPort auditPort,
                        SkillGovernancePolicy governancePolicy,
                        Clock clock) {
        if (catalogRepository == null) throw new IllegalArgumentException("SKILL_CATALOG_REPOSITORY_REQUIRED");
        if (packageRepository == null) throw new IllegalArgumentException("SKILL_PACKAGE_REPOSITORY_REQUIRED");
        if (transactionPort == null) throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        if (packageLimits == null) throw new IllegalArgumentException("SKILL_PACKAGE_LIMITS_REQUIRED");
        if (authorizationPort == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUTHORIZATION_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUDIT_REQUIRED");
        if (governancePolicy == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_POLICY_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_CLOCK_REQUIRED");
        this.catalogRepository = catalogRepository;
        this.packageRepository = packageRepository;
        this.transactionPort = transactionPort;
        this.packageLimits = packageLimits;
        this.authorizationPort = authorizationPort;
        this.auditPort = auditPort;
        this.governancePolicy = governancePolicy;
        this.clock = clock;
    }

    public SkillRollbackOutcome execute(RestoreSkillCommand command) {
        SkillGovernanceCommand governance = command.governance();
        authorizationPort.require(governance.actor(), SkillGovernancePermission.SKILL_RESTORE,
                governance.scope(), governance.projectId(), governance.skillId());
        return transactionPort.required(() -> restoreRequired(command));
    }

    private SkillRollbackOutcome restoreRequired(RestoreSkillCommand command) {
        requireStores();
        SkillGovernanceCommand governance = command.governance();
        SkillCatalogEntry current = catalogRepository.find(
                        governance.scope(), governance.projectId(), governance.skillId(), true)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + governance.skillId()));
        requireExpected(governance, current);
        governancePolicy.requireEmergencyRestore(current, command.sourceVersion());
        SkillGovernanceState before = current.governanceState();

        SkillPackageKey sourceKey = new SkillPackageKey(
                governance.scope(), governance.projectId(), governance.skillId(), command.sourceVersion());
        SkillPackageVersion source = packageRepository.findVersion(sourceKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Skill 版本不存在：" + governance.skillId() + "@" + command.sourceVersion()));
        List<SkillArtifact> sourceArtifacts = packageRepository.findArtifacts(sourceKey);

        int nextVersion = current.currentVersion() + 1;
        String name = current.name().isBlank() ? governance.skillId() : current.name();
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.rebuild(
                governance.scope(), governance.projectId(), governance.skillId(), name,
                current.description(), nextVersion, source.content(), sourceArtifacts,
                source.manifestJson(), packageLimits);
        String nextHash = SkillCatalogFingerprint.sha256(
                governance.scope(), governance.projectId(), governance.skillId(), name,
                current.description(), source.content(), nextVersion,
                current.status(), current.updateMode(), current.autoUpdateEnabled(), current.autoMergeEnabled());
        boolean advanced = catalogRepository.compareAndSetCurrent(new SkillCurrentPointerUpdate(
                governance.scope(), governance.projectId(), governance.skillId(),
                current.currentVersion(), current.currentSkillHash(), nextVersion, nextHash,
                name, current.description(), source.content(), "EMERGENCY_RESTORE",
                descriptor.packageHash(), descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes())));
        if (!advanced) throw new IllegalStateException("SKILL_CURRENT_POINTER_CONFLICT:" + governance.skillId());

        SkillPackageVersion published = new SkillPackageVersion(
                0L, new SkillPackageKey(governance.scope(), governance.projectId(), governance.skillId(), nextVersion),
                nextHash, current.currentVersion(), current.currentSkillHash(), "", "", "",
                "EMERGENCY_RESTORE", source.content(), "RESTORE", governance.actor(), governance.reason(),
                descriptor.packageHash(), descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()), descriptor.entrypoint(),
                descriptor.artifactCount(), descriptor.packageSize(), null);
        packageRepository.appendVersion(published, descriptor.artifacts().values().stream()
                .map(artifact -> new SkillArtifact(
                        artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                        artifact.contentHash(), artifact.sizeBytes(), artifact.content()))
                .toList());

        SkillGovernanceState after = new SkillGovernanceState(
                before.lifecycleStatus(), SkillMutationMode.MANUAL_ONLY, SkillExecutionMode.SHADOW_ONLY,
                before.bindingMode(), SkillLock.none(), false);
        boolean stateChanged = catalogRepository.compareAndSetGovernance(new SkillGovernanceUpdate(
                governance.scope(), governance.projectId(), governance.skillId(), nextVersion, nextHash,
                before, after));
        if (!stateChanged) throw new IllegalStateException("SKILL_GOVERNANCE_CAS_CONFLICT:" + governance.skillId());
        auditPort.record(new SkillGovernanceAuditRecord(
                "EMERGENCY_RESTORE", governance.scope(), governance.projectId(), governance.skillId(),
                governance.actor(), governance.reason(), governance.approvalId(), before, after,
                LocalDateTime.now(clock)));
        return new SkillRollbackOutcome(current, published);
    }

    private void requireExpected(SkillGovernanceCommand command, SkillCatalogEntry current) {
        if (current.currentVersion() != command.expectedVersion()
                || !current.currentSkillHash().equals(command.expectedSkillHash())) {
            throw new IllegalStateException("SKILL_CURRENT_POINTER_CONFLICT:" + command.skillId());
        }
    }

    private void requireStores() {
        if (!catalogRepository.available()) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
        if (!packageRepository.available()) throw new IllegalStateException("SKILL_PACKAGE_STORE_UNAVAILABLE");
    }
}
