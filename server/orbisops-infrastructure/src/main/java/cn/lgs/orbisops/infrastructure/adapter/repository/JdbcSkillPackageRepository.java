package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSkillPackageRepository implements ISkillPackageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillPackageRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public Optional<SkillPackageVersion> findVersion(SkillPackageKey key) {
        return requiredTemplate().query(VERSION_SELECT + " WHERE scope=? AND project_id=? AND skill_id=? AND version=? LIMIT 1",
                this::version, key.scope(), key.projectId(), key.skillId(), key.version()).stream().findFirst();
    }

    @Override
    public List<SkillPackageVersion> findVersions(String scope, String projectId, String skillId) {
        return requiredTemplate().query(VERSION_SELECT
                        + " WHERE scope=? AND project_id=? AND skill_id=? ORDER BY version DESC, id DESC",
                this::version, scope, projectId == null ? "" : projectId, skillId);
    }

    @Override
    public Optional<SkillArtifact> findArtifact(SkillPackageKey key, String artifactPath) {
        return requiredTemplate().query(ARTIFACT_SELECT
                        + " WHERE scope=? AND project_id=? AND skill_id=? AND version=? AND artifact_path=? LIMIT 1",
                this::artifact, key.scope(), key.projectId(), key.skillId(), key.version(), artifactPath)
                .stream().findFirst();
    }

    @Override
    public List<SkillArtifact> findArtifacts(SkillPackageKey key) {
        return requiredTemplate().query(ARTIFACT_SELECT
                        + " WHERE scope=? AND project_id=? AND skill_id=? AND version=? ORDER BY artifact_path ASC",
                this::artifact, key.scope(), key.projectId(), key.skillId(), key.version());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void appendVersion(SkillPackageVersion version, List<SkillArtifact> artifacts) {
        requireCompleteVersion(version, artifacts);
        Optional<SkillPackageVersion> existing = findVersion(version.key());
        if (existing.isPresent()) {
            verifyIdempotent(existing.get(), version, artifacts);
            return;
        }
        try {
            requiredTemplate().update("""
                    INSERT INTO ai_ops_skill_version
                      (skill_id, project_id, scope, version, skill_hash, base_version, base_skill_hash,
                       source_run_id, source_session_id, evolution_job_id, publish_mode,
                       content, source_type, source_trace_id, change_summary,
                       package_hash, manifest_json, artifact_hashes_json, entrypoint, artifact_count, package_size)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, version.key().skillId(), version.key().projectId(), version.key().scope(),
                    version.key().version(), version.skillHash(), version.baseVersion(), version.baseSkillHash(),
                    version.sourceRunId(), version.sourceSessionId(), version.evolutionJobId(), version.publishMode(),
                    version.content(), version.sourceType(), version.sourceTraceId(), version.changeSummary(),
                    version.packageHash(), version.manifestJson(), version.artifactHashesJson(), version.entrypoint(),
                    version.artifactCount(), version.packageSize());
            for (SkillArtifact artifact : List.copyOf(artifacts)) {
                insertArtifact(version.key(), version.packageHash(), artifact);
            }
        } catch (DuplicateKeyException conflict) {
            SkillPackageVersion concurrent = findVersion(version.key())
                    .orElseThrow(() -> new IllegalStateException("SKILL_PACKAGE_VERSION_APPEND_CONFLICT", conflict));
            verifyIdempotent(concurrent, version, artifacts);
        }
    }

    private void verifyIdempotent(SkillPackageVersion stored,
                                  SkillPackageVersion requested,
                                  List<SkillArtifact> requestedArtifacts) {
        List<SkillArtifact> storedArtifacts = findArtifacts(stored.key());
        if (!stored.sameImmutableContent(requested) || !storedArtifacts.equals(List.copyOf(requestedArtifacts))) {
            throw new IllegalStateException("SKILL_PACKAGE_VERSION_IMMUTABLE_CONFLICT："
                    + requested.key().skillId() + "@" + requested.key().version());
        }
    }

    private void requireCompleteVersion(SkillPackageVersion version, List<SkillArtifact> artifacts) {
        if (version == null || version.packageHash().isBlank() || version.manifestJson().isBlank()
                || version.artifactHashesJson().isBlank() || version.entrypoint().isBlank()
                || version.artifactCount() <= 0 || artifacts == null
                || version.artifactCount() != artifacts.size()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_VERSION_INCOMPLETE");
        }
    }

    private void insertArtifact(SkillPackageKey key, String packageHash, SkillArtifact artifact) {
        requiredTemplate().update("""
                INSERT INTO ai_ops_skill_artifact
                  (artifact_id, scope, project_id, skill_id, version, package_hash, artifact_path,
                   artifact_role, media_type, content_encoding, content_hash, size_bytes, content)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ska-" + UUID.randomUUID(), key.scope(), key.projectId(), key.skillId(), key.version(),
                packageHash, artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                artifact.contentHash(), artifact.sizeBytes(), artifact.content());
    }

    private SkillPackageVersion version(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        return new SkillPackageVersion(rs.getLong("id"),
                new SkillPackageKey(rs.getString("scope"), rs.getString("project_id"),
                        rs.getString("skill_id"), rs.getInt("version")),
                rs.getString("skill_hash"), rs.getInt("base_version"), rs.getString("base_skill_hash"),
                rs.getString("source_run_id"), rs.getString("source_session_id"),
                rs.getString("evolution_job_id"), rs.getString("publish_mode"), rs.getString("content"),
                rs.getString("source_type"), rs.getString("source_trace_id"), rs.getString("change_summary"),
                rs.getString("package_hash"), rs.getString("manifest_json"), rs.getString("artifact_hashes_json"),
                rs.getString("entrypoint"), rs.getInt("artifact_count"), rs.getLong("package_size"),
                created == null ? null : created.toInstant());
    }

    private SkillArtifact artifact(ResultSet rs, int rowNum) throws SQLException {
        return new SkillArtifact(rs.getString("artifact_path"), rs.getString("artifact_role"),
                rs.getString("media_type"), rs.getString("content_encoding"), rs.getString("content_hash"),
                rs.getLong("size_bytes"), rs.getString("content"));
    }

    private JdbcTemplate requiredTemplate() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("SKILL_PACKAGE_STORE_UNAVAILABLE");
        }
        return jdbcTemplate;
    }

    private static final String VERSION_SELECT = """
            SELECT id, skill_id, project_id, scope, version, content, source_type, source_trace_id,
                   change_summary, skill_hash, base_version, base_skill_hash, source_run_id,
                   source_session_id, evolution_job_id, publish_mode, package_hash, manifest_json,
                   artifact_hashes_json, entrypoint, artifact_count, package_size, create_time
            FROM ai_ops_skill_version
            """;

    private static final String ARTIFACT_SELECT = """
            SELECT artifact_path, artifact_role, media_type, content_encoding, content_hash, size_bytes, content
            FROM ai_ops_skill_artifact
            """;
}
