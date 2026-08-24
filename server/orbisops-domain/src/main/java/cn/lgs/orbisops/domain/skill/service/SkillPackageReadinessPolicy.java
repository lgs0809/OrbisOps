package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Rechecks stored bytes, not an author-supplied success flag, before runtime publication. */
public final class SkillPackageReadinessPolicy {
    public void requireReady(SkillRuntimeCandidate c, SkillPackageVersion version, List<SkillArtifact> artifacts) {
        require(version != null && version.key().equals(new SkillPackageKey(c.scope(), c.projectId(), c.skillId(), c.version())));
        require(version.skillHash().equals(c.skillHash()) && version.packageHash().equals(c.packageHash()));
        require(CanonicalObjectHasher.sha256Text(version.manifestJson()).equals(c.manifestHash()));
        Map<String, String> hashes = new TreeMap<>();
        long bytes = 0;
        for (var artifact : artifacts) {
            byte[] raw = switch (artifact.encoding()) {
                case "UTF8" -> artifact.content().getBytes(StandardCharsets.UTF_8);
                case "BASE64" -> Base64.getDecoder().decode(artifact.content());
                default -> throw new IllegalStateException("SKILL_PACKAGE_ENCODING_UNSUPPORTED");
            };
            String hash = digest(raw);
            require(raw.length == artifact.sizeBytes() && hash.equals(artifact.contentHash()));
            require(hashes.put(artifact.path(), hash) == null);
            bytes += raw.length;
            if (artifact.path().equals(c.entrypoint())) require(artifact.content().equals(version.content()));
        }
        require(hashes.containsKey(c.entrypoint()) && hashes.equals(c.artifactHashes()));
        require(hashes.equals(CanonicalJson.parseObject(version.artifactHashesJson())));
        require(version.artifactCount() == hashes.size() && version.packageSize() == bytes);
        require(CanonicalObjectHasher.sha256(Map.of("manifestHash", c.manifestHash(), "artifactHashes", hashes)).equals(c.packageHash()));
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void require(boolean valid) {
        if (!valid) throw new IllegalStateException("SKILL_PACKAGE_NOT_READY_FOR_RUNTIME_PUBLICATION");
    }
}
