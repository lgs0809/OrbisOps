package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Forks a SEALED immutable package; the source catalog pointer is never changed. */
public final class ForkSealedSkillUseCase {

    private final ISkillCatalogRepository catalogRepository;
    private final ISkillPackageRepository packageRepository;
    private final SkillTransactionPort transactionPort;
    private final SkillPackageManifest.Limits packageLimits;
    private final SkillGovernanceAuthorizationPort authorizationPort;
    private final SkillGovernanceAuditPort auditPort;
    private final Clock clock;

    public ForkSealedSkillUseCase(ISkillCatalogRepository catalogRepository,
                                  ISkillPackageRepository packageRepository,
                                  SkillTransactionPort transactionPort,
                                  SkillPackageManifest.Limits packageLimits,
                                  SkillGovernanceAuthorizationPort authorizationPort,
                                  SkillGovernanceAuditPort auditPort) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits,
                authorizationPort, auditPort, Clock.systemUTC());
    }

    ForkSealedSkillUseCase(ISkillCatalogRepository catalogRepository,
                           ISkillPackageRepository packageRepository,
                           SkillTransactionPort transactionPort,
                           SkillPackageManifest.Limits packageLimits,
                           SkillGovernanceAuthorizationPort authorizationPort,
                           SkillGovernanceAuditPort auditPort,
                           Clock clock) {
        if (catalogRepository == null) throw new IllegalArgumentException("SKILL_CATALOG_REPOSITORY_REQUIRED");
        if (packageRepository == null) throw new IllegalArgumentException("SKILL_PACKAGE_REPOSITORY_REQUIRED");
        if (transactionPort == null) throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        if (packageLimits == null) throw new IllegalArgumentException("SKILL_PACKAGE_LIMITS_REQUIRED");
        if (authorizationPort == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUTHORIZATION_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUDIT_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_CLOCK_REQUIRED");
        this.catalogRepository = catalogRepository;
        this.packageRepository = packageRepository;
        this.transactionPort = transactionPort;
        this.packageLimits = packageLimits;
        this.authorizationPort = authorizationPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public SkillCatalogWriteOutcome execute(ForkSealedSkillCommand command) {
        SkillGovernanceCommand sourceCommand = command.source();
        authorizationPort.require(sourceCommand.actor(), SkillGovernancePermission.SKILL_FORK,
                sourceCommand.scope(), sourceCommand.projectId(), sourceCommand.skillId());
        return transactionPort.required(() -> forkRequired(command));
    }

    private SkillCatalogWriteOutcome forkRequired(ForkSealedSkillCommand command) {
        requireStores();
        SkillGovernanceCommand sourceCommand = command.source();
        SkillCatalogEntry sourceEntry = catalogRepository.find(
                        sourceCommand.scope(), sourceCommand.projectId(), sourceCommand.skillId(), true)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + sourceCommand.skillId()));
        requireExpected(sourceCommand, sourceEntry);
        if (sourceEntry.governanceState().mutationMode() != SkillMutationMode.SEALED) {
            throw new IllegalStateException("SKILL_FORK_REQUIRES_SEALED_SOURCE");
        }
        if (catalogRepository.find(sourceCommand.scope(), sourceCommand.projectId(),
                command.targetSkillId(), false).isPresent()) {
            throw new IllegalArgumentException("Skill 已存在：" + command.targetSkillId());
        }

        SkillPackageKey sourceKey = new SkillPackageKey(
                sourceCommand.scope(), sourceCommand.projectId(), sourceCommand.skillId(),
                sourceEntry.currentVersion());
        SkillPackageVersion sourceVersion = packageRepository.findVersion(sourceKey)
                .orElseThrow(() -> new IllegalStateException("SKILL_CURRENT_PACKAGE_MISSING"));
        List<SkillArtifact> sourceArtifacts = packageRepository.findArtifacts(sourceKey);
        SkillGovernanceState targetState = SkillGovernanceState.activeDefault();
        int targetVersion = 1;
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.rebuild(
                sourceCommand.scope(), sourceCommand.projectId(), command.targetSkillId(), command.targetName(),
                sourceEntry.description(), targetVersion, sourceVersion.content(), sourceArtifacts,
                sourceVersion.manifestJson(), packageLimits);
        String targetHash = SkillCatalogFingerprint.sha256(
                sourceCommand.scope(), sourceCommand.projectId(), command.targetSkillId(), command.targetName(),
                sourceEntry.description(), sourceVersion.content(), targetVersion,
                targetState.legacyStatusProjection(), targetState.legacyUpdateModeProjection(), true, true);
        SkillCatalogEntry targetEntry = new SkillCatalogEntry(
                0L, command.targetSkillId(), sourceCommand.projectId(), command.targetName(),
                sourceCommand.scope(), "GLOBAL".equals(sourceCommand.scope()) ? "" : sourceCommand.skillId(),
                sourceEntry.description(), sourceVersion.content(), targetVersion,
                targetState.legacyStatusProjection(), sourceCommand.actor(), null, null,
                "FORKED", targetState.legacyUpdateModeProjection(), true, true, null,
                "", "", null, targetHash, targetVersion, targetHash, targetVersion,
                descriptor.packageHash(), descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()), targetState);
        if (!catalogRepository.insertIfAbsent(targetEntry)) {
            throw new IllegalArgumentException("Skill 已存在：" + command.targetSkillId());
        }
        SkillPackageVersion published = new SkillPackageVersion(
                0L, new SkillPackageKey(sourceCommand.scope(), sourceCommand.projectId(),
                        command.targetSkillId(), targetVersion),
                targetHash, sourceEntry.currentVersion(), sourceEntry.currentSkillHash(), "", "", "",
                "SEALED_FORK", sourceVersion.content(), "FORK", sourceCommand.actor(),
                sourceCommand.reason(), descriptor.packageHash(), descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()), descriptor.entrypoint(),
                descriptor.artifactCount(), descriptor.packageSize(), null);
        packageRepository.appendVersion(published, descriptor.artifacts().values().stream()
                .map(artifact -> new SkillArtifact(
                        artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                        artifact.contentHash(), artifact.sizeBytes(), artifact.content()))
                .toList());
        auditPort.record(new SkillGovernanceAuditRecord(
                "FORK_SEALED", sourceCommand.scope(), sourceCommand.projectId(), sourceCommand.skillId(),
                sourceCommand.actor(), sourceCommand.reason() + "; target=" + command.targetSkillId(),
                sourceCommand.approvalId(), sourceEntry.governanceState(), targetState,
                LocalDateTime.now(clock)));
        return new SkillCatalogWriteOutcome(true, null, published);
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
