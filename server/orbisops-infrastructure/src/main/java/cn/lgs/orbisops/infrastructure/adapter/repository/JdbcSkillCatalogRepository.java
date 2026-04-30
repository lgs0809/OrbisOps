package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.model.SkillBindingMode;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogMutation;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleStatus;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillLockType;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSkillCatalogRepository implements ISkillCatalogRepository {

    private static final String SELECT_COLUMNS = """
            id, skill_id, project_id, skill_name, scope, source_global_skill_id,
            description, content, version, status, create_by, create_time, update_time,
            origin, update_mode, lifecycle_status, mutation_mode, execution_mode, binding_mode,
            lock_type, lock_reason, lock_actor, lock_approval_id, lock_at,
            legacy_frozen_classification_required,
            auto_update_enabled, auto_merge_enabled, last_evolved_at,
            frozen_reason, frozen_by, frozen_at, skill_hash, current_version, current_skill_hash, version_seq,
            current_package_hash, package_manifest_json, artifact_hashes_json
            """;

    @Qualifier("mysqlJdbcTemplate")
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public JdbcSkillCatalogRepository() { }
    public JdbcSkillCatalogRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate=jdbcTemplate; }

    Optional<SkillCatalogEntry> lockEvolutionEntry(String scope,String projectId,String skillId) {
        requireAvailable();
        return jdbcTemplate.query("SELECT "+SELECT_COLUMNS+" FROM ai_ops_skill WHERE scope=? AND project_id=? AND skill_id=? FOR UPDATE",
                mapper(),scope,projectId,skillId).stream().findFirst();
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public List<SkillCatalogEntry> findAll(String scope, String projectId, boolean includeContent) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_skill "
                        + "WHERE scope=? AND project_id=? ORDER BY update_time DESC, id DESC",
                mapper(), scope, projectId);
    }

    @Override
    public List<SkillCatalogEntry> findEvolutionMetadata(String scope, String projectId) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS.replace("description, content,", "description, '' AS content,")
                        + " FROM ai_ops_skill WHERE scope=? AND project_id=? ORDER BY skill_id",
                mapper(), scope, projectId);
    }

    @Override public List<SkillCatalogEntry> findAuthoringMetadata(String scope,String projectId) {
        return new JdbcSkillLifecycleVisibility(jdbcTemplate).filter(projectId,findEvolutionMetadata(scope,projectId),
                SkillCatalogEntry::scope,SkillCatalogEntry::skillId,java.util.Set.of());
    }

    List<SkillCatalogEntry> routingMetadataPage(long afterId,int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT "+SELECT_COLUMNS.replace("description, content,","description, '' AS content,")
                +" FROM ai_ops_skill WHERE id>? ORDER BY id LIMIT ?",mapper(),afterId,limit);
    }

    @Override
    public Optional<SkillCatalogEntry> find(String scope, String projectId, String skillId, boolean includeContent) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_skill "
                        + "WHERE scope=? AND project_id=? AND skill_id=? LIMIT 1",
                mapper(), scope, projectId, skillId).stream().findFirst();
    }

    @Override
    public boolean insertIfAbsent(SkillCatalogEntry entry) {
        requireAvailable();
        SkillGovernanceState state = entry.governanceState();
        SkillLock lock = state.lock();
        try {
            return jdbcTemplate.update("""
                            INSERT INTO ai_ops_skill
                            (skill_id, project_id, skill_name, scope, source_global_skill_id,
                             description, content, version, status, create_by,
                             origin, update_mode,
                             lifecycle_status, mutation_mode, execution_mode, binding_mode,
                             lock_type, lock_reason, lock_actor, lock_approval_id, lock_at,
                             legacy_frozen_classification_required,
                             auto_update_enabled, auto_merge_enabled, last_evolved_at,
                             frozen_reason, frozen_by, frozen_at,
                             skill_hash, current_version, current_skill_hash, version_seq,
                             current_package_hash, package_manifest_json, artifact_hashes_json)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    entry.skillId(), entry.projectId(), entry.name(), entry.scope(), entry.sourceGlobalSkillId(),
                    entry.description(), entry.content(), entry.version(), state.legacyStatusProjection(), entry.createBy(),
                    entry.origin(), state.legacyUpdateModeProjection(),
                    state.lifecycleStatus().name(), state.mutationMode().name(), state.executionMode().name(),
                    state.bindingMode().name(), lock.type().name(), lock.reason(), lock.actor(), lock.approvalId(),
                    timestamp(lock.lockedAt()), state.legacyFrozenClassificationRequired() ? 1 : 0,
                    entry.autoUpdateEnabled() ? 1 : 0, entry.autoMergeEnabled() ? 1 : 0,
                    timestamp(entry.lastEvolvedAt()), lock.reason(), lock.actor(), timestamp(lock.lockedAt()),
                    entry.skillHash(), entry.currentVersion(), entry.currentSkillHash(), entry.versionSeq(),
                    entry.currentPackageHash(), entry.packageManifestJson(), entry.artifactHashesJson()) == 1;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    @Override
    public boolean compareAndSetMutation(SkillCatalogMutation mutation) {
        requireAvailable();
        SkillGovernanceState state = mutation.governanceState();
        SkillLock lock = state.lock();
        return jdbcTemplate.update("""
                UPDATE ai_ops_skill
                SET skill_name=?, source_global_skill_id=?, description=?, content=?,
                    version=?, status=?, origin=?, update_mode=?,
                    lifecycle_status=?, mutation_mode=?, execution_mode=?, binding_mode=?,
                    lock_type=?, lock_reason=?, lock_actor=?, lock_approval_id=?, lock_at=?,
                    legacy_frozen_classification_required=?,
                    auto_update_enabled=?, auto_merge_enabled=?, last_evolved_at=?,
                    frozen_reason=?, frozen_by=?, frozen_at=?,
                    skill_hash=?, current_version=?, current_skill_hash=?, version_seq=version_seq + 1,
                    current_package_hash=?, package_manifest_json=?, artifact_hashes_json=?,
                    update_time=CURRENT_TIMESTAMP
                WHERE scope=? AND project_id=? AND skill_id=?
                  AND current_version=? AND COALESCE(current_skill_hash, '')=?
                """,
                mutation.name(), mutation.sourceGlobalSkillId(), mutation.description(), mutation.content(),
                mutation.nextVersion(), state.legacyStatusProjection(), mutation.origin(),
                state.legacyUpdateModeProjection(), state.lifecycleStatus().name(), state.mutationMode().name(),
                state.executionMode().name(), state.bindingMode().name(), lock.type().name(), lock.reason(),
                lock.actor(), lock.approvalId(), timestamp(lock.lockedAt()),
                state.legacyFrozenClassificationRequired() ? 1 : 0,
                mutation.autoUpdateEnabled() ? 1 : 0, mutation.autoMergeEnabled() ? 1 : 0,
                timestamp(mutation.lastEvolvedAt()), lock.reason(), lock.actor(), timestamp(lock.lockedAt()),
                mutation.nextSkillHash(), mutation.nextVersion(), mutation.nextSkillHash(),
                mutation.packageHash(), mutation.manifestJson(), mutation.artifactHashesJson(),
                mutation.scope(), mutation.projectId(), mutation.skillId(),
                mutation.baseVersion(), mutation.baseSkillHash()) == 1;
    }

    @Override
    public boolean compareAndSetEvolution(SkillEvolutionUpdate update) {
        requireAvailable();
        return jdbcTemplate.update("""
                UPDATE ai_ops_skill
                SET skill_name=?, description=?, content=?, version=?, current_version=?, current_skill_hash=?,
                    version_seq=version_seq + 1, origin='EVOLVED', last_evolved_at=CURRENT_TIMESTAMP,
                    skill_hash=?, current_package_hash=?, package_manifest_json=?, artifact_hashes_json=?,
                    update_time=CURRENT_TIMESTAMP
                WHERE scope='PROJECT' AND project_id=? AND skill_id=?
                  AND current_version=? AND current_skill_hash=?
                  AND lifecycle_status='ACTIVE'
                  AND mutation_mode='AUTO'
                  AND execution_mode IN ('ENABLED', 'SHADOW_ONLY')
                  AND legacy_frozen_classification_required=0
                  AND auto_update_enabled=1
                """, update.name(), update.description(), update.content(), update.nextVersion(), update.nextVersion(),
                update.nextSkillHash(), update.nextSkillHash(), update.packageHash(), update.manifestJson(),
                update.artifactHashesJson(), update.projectId(), update.skillId(), update.baseVersion(),
                update.baseSkillHash()) == 1;
    }

    @Override
    public boolean compareAndSetCurrent(SkillCurrentPointerUpdate update) {
        requireAvailable();
        return jdbcTemplate.update("""
                UPDATE ai_ops_skill
                SET skill_name=?, description=?, content=?, version=?, current_version=?, current_skill_hash=?,
                    version_seq=version_seq + 1, origin=?, skill_hash=?, current_package_hash=?,
                    package_manifest_json=?, artifact_hashes_json=?, update_time=CURRENT_TIMESTAMP
                WHERE scope=? AND project_id=? AND skill_id=?
                  AND current_version=? AND COALESCE(current_skill_hash, '')=?
                """,
                update.name(), update.description(), update.content(), update.nextVersion(), update.nextVersion(),
                update.nextSkillHash(), update.origin(), update.nextSkillHash(), update.packageHash(),
                update.manifestJson(), update.artifactHashesJson(), update.scope(), update.projectId(),
                update.skillId(), update.baseVersion(), update.baseSkillHash()) == 1;
    }

    @Override
    public boolean compareAndSetGovernance(SkillGovernanceUpdate update) {
        requireAvailable();
        SkillGovernanceState expected = update.expectedState();
        SkillGovernanceState next = update.nextState();
        SkillLock nextLock = next.lock();
        return jdbcTemplate.update("""
                UPDATE ai_ops_skill
                SET status=?, update_mode=?, lifecycle_status=?, mutation_mode=?, execution_mode=?, binding_mode=?,
                    lock_type=?, lock_reason=?, lock_actor=?, lock_approval_id=?, lock_at=?,
                    legacy_frozen_classification_required=?,
                    frozen_reason=?, frozen_by=?, frozen_at=?,
                    version_seq=version_seq + 1, update_time=CURRENT_TIMESTAMP
                WHERE scope=? AND project_id=? AND skill_id=?
                  AND current_version=? AND COALESCE(current_skill_hash, '')=?
                  AND lifecycle_status=? AND mutation_mode=? AND execution_mode=? AND binding_mode=?
                  AND lock_type=? AND legacy_frozen_classification_required=?
                """,
                next.legacyStatusProjection(), next.legacyUpdateModeProjection(),
                next.lifecycleStatus().name(), next.mutationMode().name(), next.executionMode().name(),
                next.bindingMode().name(), nextLock.type().name(), nextLock.reason(), nextLock.actor(),
                nextLock.approvalId(), timestamp(nextLock.lockedAt()),
                next.legacyFrozenClassificationRequired() ? 1 : 0,
                nextLock.reason(), nextLock.actor(), timestamp(nextLock.lockedAt()),
                update.scope(), update.projectId(), update.skillId(), update.expectedVersion(),
                update.expectedSkillHash(), expected.lifecycleStatus().name(), expected.mutationMode().name(),
                expected.executionMode().name(), expected.bindingMode().name(), expected.lock().type().name(),
                expected.legacyFrozenClassificationRequired() ? 1 : 0) == 1;
    }

    private RowMapper<SkillCatalogEntry> mapper() {
        return (rs, rowNum) -> {
            String status = rs.getString("status");
            String updateMode = rs.getString("update_mode");
            SkillGovernanceState governanceState = governanceState(rs, status, updateMode);
            return new SkillCatalogEntry(
                    rs.getLong("id"), rs.getString("skill_id"), rs.getString("project_id"),
                    rs.getString("skill_name"), rs.getString("scope"), rs.getString("source_global_skill_id"),
                    rs.getString("description"), rs.getString("content"),
                    rs.getInt("version"), status, rs.getString("create_by"),
                    localDateTime(rs.getTimestamp("create_time")), localDateTime(rs.getTimestamp("update_time")),
                    rs.getString("origin"), updateMode, rs.getInt("auto_update_enabled") == 1,
                    rs.getInt("auto_merge_enabled") == 1, localDateTime(rs.getTimestamp("last_evolved_at")),
                    rs.getString("frozen_reason"), rs.getString("frozen_by"), localDateTime(rs.getTimestamp("frozen_at")),
                    rs.getString("skill_hash"), rs.getInt("current_version"), rs.getString("current_skill_hash"),
                    rs.getInt("version_seq"), rs.getString("current_package_hash"),
                    rs.getString("package_manifest_json"), rs.getString("artifact_hashes_json"), governanceState);
        };
    }

    private SkillGovernanceState governanceState(ResultSet rs,
                                                  String status,
                                                  String updateMode) throws SQLException {
        if ("FROZEN".equalsIgnoreCase(text(status))
                || "FROZEN".equalsIgnoreCase(text(updateMode))
                || rs.getInt("legacy_frozen_classification_required") == 1) {
            return SkillGovernanceState.fromLegacy(
                    "FROZEN", "FROZEN",
                    firstText(rs.getString("lock_reason"), rs.getString("frozen_reason")),
                    firstText(rs.getString("lock_actor"), rs.getString("frozen_by")),
                    firstTime(rs.getTimestamp("lock_at"), rs.getTimestamp("frozen_at"), rs.getTimestamp("create_time")));
        }
        try {
            SkillLifecycleStatus lifecycle = SkillLifecycleStatus.require(rs.getString("lifecycle_status"));
            SkillMutationMode mutation = SkillMutationMode.require(rs.getString("mutation_mode"));
            SkillExecutionMode execution = SkillExecutionMode.require(rs.getString("execution_mode"));
            SkillBindingMode binding = SkillBindingMode.require(rs.getString("binding_mode"));
            SkillLockType lockType = SkillLockType.require(rs.getString("lock_type"));
            if ((mutation == SkillMutationMode.LOCKED || mutation == SkillMutationMode.SEALED)
                    && lockType == SkillLockType.NONE) {
                throw new IllegalArgumentException("SKILL_MUTATION_LOCK_METADATA_REQUIRED");
            }
            SkillLock lock = lockType == SkillLockType.NONE
                    ? SkillLock.none()
                    : new SkillLock(
                            lockType,
                            firstText(rs.getString("lock_reason"), "governance lock"),
                            firstText(rs.getString("lock_actor"), "SYSTEM_GOVERNANCE"),
                            rs.getString("lock_approval_id"),
                            firstTime(rs.getTimestamp("lock_at"), rs.getTimestamp("frozen_at"), rs.getTimestamp("create_time")));
            return new SkillGovernanceState(lifecycle, mutation, execution, binding, lock, false);
        } catch (RuntimeException invalid) {
            return invalidGovernanceState(rs, invalid.getMessage());
        }
    }

    private SkillGovernanceState invalidGovernanceState(ResultSet rs, String detail) throws SQLException {
        LocalDateTime time = firstTime(
                rs.getTimestamp("lock_at"), rs.getTimestamp("frozen_at"), rs.getTimestamp("create_time"));
        return new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE,
                SkillMutationMode.LOCKED,
                SkillExecutionMode.QUARANTINED,
                SkillBindingMode.FLOATING,
                new SkillLock(
                        SkillLockType.LEGACY_UNCLASSIFIED,
                        "invalid governance state: " + firstText(detail, "unknown"),
                        "SYSTEM_GOVERNANCE_READER",
                        "",
                        time == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : time),
                true);
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private LocalDateTime firstTime(Timestamp... values) {
        for (Timestamp value : values) {
            if (value != null) return value.toLocalDateTime();
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!text(value).isBlank()) return text(value);
        }
        return "";
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
