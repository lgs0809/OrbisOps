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

    public SkillRollbackUseCase(ISkillCatalogRepository catalogRepository,
                                ISkillPackageRepository packageRepository,
                                SkillTransactionPort transactionPort,
                                SkillPackageManifest.Limits packageLimits) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits,
                new SkillGovernancePolicy());
    }

    SkillRollbackUseCase(ISkillCatalogRepository catalogRepository,
                         ISkillPackageRepository packageRepository,
                         SkillTransactionPort transactionPort,
                         SkillPackageManifest.Limits packageLimits,
                         SkillGovernancePolicy governancePolicy) {
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
        requireStores();
        SkillCatalogEntry current = catalogRepository.find(scope, projectId, skillId, true)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + skillId));
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
