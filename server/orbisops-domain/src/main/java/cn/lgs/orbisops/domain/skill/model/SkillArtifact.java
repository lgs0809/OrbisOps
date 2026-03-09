package cn.lgs.orbisops.domain.skill.model;

public record SkillArtifact(String path,
                            String role,
                            String mediaType,
                            String encoding,
                            String contentHash,
                            long sizeBytes,
                            String content) {

    public SkillArtifact {
        path = requireText(path, "path");
        role = requireText(role, "role");
        mediaType = requireText(mediaType, "mediaType");
        encoding = requireText(encoding, "encoding");
        contentHash = requireText(contentHash, "contentHash");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("SKILL_ARTIFACT_SIZE_INVALID");
        }
        content = content == null ? "" : content;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("SKILL_ARTIFACT_" + field.toUpperCase() + "_REQUIRED");
        }
        return value.trim();
    }
}
