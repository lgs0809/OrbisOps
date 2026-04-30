package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillEvolutionPublishUseCase {

    private final ISkillCatalogRepository catalogRepository;
    private final ISkillPackageRepository packageRepository;
    private final SkillTransactionPort transactionPort;
    private final SkillPackageManifest.Limits packageLimits;
    private final SkillGovernancePolicy governancePolicy;

    public SkillEvolutionPublishUseCase(ISkillCatalogRepository catalogRepository,
                                        ISkillPackageRepository packageRepository,
                                        SkillTransactionPort transactionPort,
                                        SkillPackageManifest.Limits packageLimits) {
        this(catalogRepository, packageRepository, transactionPort, packageLimits,
                new SkillGovernancePolicy());
    }

    SkillEvolutionPublishUseCase(ISkillCatalogRepository catalogRepository,
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

    public SkillEvolutionPublishOutcome publishProject(String projectId,
                                                        String skillId,
                                                        Map<String, Object> request,
                                                        int baseVersion,
                                                        String baseSkillHash,
                                                        String actor) {
        String project = required(projectId, "SKILL_PROJECT_ID_REQUIRED");
        String normalizedSkillId = SkillCatalogFingerprint.normalizeId(
                required(skillId, "SKILL_ID_REQUIRED"));
        if (normalizedSkillId.isBlank()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        if (baseVersion <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        String baseHash = required(baseSkillHash, "SKILL_BASE_HASH_REQUIRED");
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> mutation = request == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        mutation.remove("actor");
        return transactionPort.required(() -> publishRequired(
                project, normalizedSkillId, mutation, baseVersion, baseHash, operator));
    }

    private SkillEvolutionPublishOutcome publishRequired(String projectId,
                                                          String skillId,
                                                          Map<String, Object> mutation,
                                                          int baseVersion,
                                                          String baseSkillHash,
                                                          String actor) {
        requireStores();
        SkillCatalogEntry current = catalogRepository.find("PROJECT", projectId, skillId, true)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + skillId));
        String skipReason = governancePolicy.autoPublishSkipReason(current);
        if (!skipReason.isBlank()) {
            return new SkillEvolutionPublishOutcome(false, skipReason, current, null);
        }
        governancePolicy.requireEvolutionPublish(current, baseVersion, baseSkillHash);

        int nextVersion = current.currentVersion() + 1;
        String currentName = current.name().isBlank() ? skillId : current.name();
        String name = mutation.containsKey("name")
                ? text(mutation.get("name"), text(mutation.get("skillName"), currentName))
                : text(mutation.get("skillName"), currentName);
        String description = mutation.containsKey("description")
                ? text(mutation.get("description"), "") : current.description();
        String content = mutation.containsKey("content")
                ? rawText(mutation.get("content"))
                : mutation.containsKey("markdown")
                ? rawText(mutation.get("markdown")) : current.content();
        List<SkillArtifact> currentArtifacts = packageRepository.findArtifacts(current.key());
        SkillPackageManifest.Descriptor descriptor = SkillPackageManifest.evolve(
                "PROJECT", projectId, skillId, name, description, nextVersion, content,
                mutation, currentArtifacts, current.packageManifestJson(), packageLimits);
        String nextHash = SkillCatalogFingerprint.sha256(
                "PROJECT", projectId, skillId, name, description, content, nextVersion,
                current.status(), current.updateMode(), current.autoUpdateEnabled(), current.autoMergeEnabled());

        boolean advanced = catalogRepository.compareAndSetEvolution(new SkillEvolutionUpdate(
                projectId, skillId, current.currentVersion(), current.currentSkillHash(),
                nextVersion, nextHash, name, description, content, descriptor.packageHash(),
                descriptor.manifestJson(), CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes())));
        if (!advanced) {
            SkillCatalogEntry reloaded = catalogRepository.find("PROJECT", projectId, skillId, true)
                    .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + skillId));
            String reason = governancePolicy.autoPublishSkipReason(reloaded);
            return new SkillEvolutionPublishOutcome(
                    false, reason.isBlank() ? "MVCC_CONFLICT" : reason, reloaded, null);
        }

        SkillPackageVersion published = new SkillPackageVersion(
                0L,
                new SkillPackageKey("PROJECT", projectId, skillId, nextVersion),
                nextHash,
                current.currentVersion(),
                current.currentSkillHash(),
                text(mutation.get("sourceRunId"), ""),
                text(mutation.get("sourceSessionId"), ""),
                text(mutation.get("evolutionJobId"), ""),
                "AUTO_EVOLVER",
                content,
                "SKILL_EVOLVER",
                firstText(mutation, actor, "sourceTraceId", "sourceRunId"),
                text(mutation.get("changeSummary"), "Skill Evolver auto publish"),
                descriptor.packageHash(),
                descriptor.manifestJson(),
                CanonicalJson.stringifyPreservingOrder(descriptor.artifactHashes()),
                descriptor.entrypoint(),
                descriptor.artifactCount(),
                descriptor.packageSize(),
                null);
        List<SkillArtifact> artifacts = descriptor.artifacts().values().stream()
                .map(artifact -> new SkillArtifact(
                        artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                        artifact.contentHash(), artifact.sizeBytes(), artifact.content()))
                .toList();
        packageRepository.appendVersion(published, artifacts);
        SkillCatalogEntry after = catalogRepository.find("PROJECT", projectId, skillId, true)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + skillId));
        return new SkillEvolutionPublishOutcome(true, "PUBLISHED", after, published);
    }

    private void requireStores() {
        if (!catalogRepository.available()) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
        if (!packageRepository.available()) throw new IllegalStateException("SKILL_PACKAGE_STORE_UNAVAILABLE");
    }

    private String firstText(Map<String, Object> source, String fallback, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key), "");
            if (!value.isBlank()) return value;
        }
        return fallback;
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
