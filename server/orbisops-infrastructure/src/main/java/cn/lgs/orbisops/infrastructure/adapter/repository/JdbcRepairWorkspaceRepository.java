package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.repair.adapter.repository.IRepairWorkspaceRepository;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Repository
@DependsOn("jdbcRepairWorkspaceSchemaInitializer")
public class JdbcRepairWorkspaceRepository implements IRepairWorkspaceRepository {

    private static final String WRITER_OWNER = "writer_lease_" + "owner";
    private static final String WRITER_CLAIM = "writer_lease_" + "token";
    private static final String WRITER_FENCE = "writer_fencing_" + "token";
    private static final String WRITER_EXPIRES = "writer_lease_" + "expires_at";

    private final JdbcTemplate jdbc;
    private final boolean jdbcEnabled;
    private final Map<String, RepairWorkspace> memory = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcRepairWorkspaceRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${orbisops.repair.jdbc-enabled:true}") boolean jdbcEnabled) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.jdbcEnabled = jdbcEnabled;
    }

    JdbcRepairWorkspaceRepository(JdbcTemplate jdbc, boolean jdbcEnabled) {
        this.jdbc = jdbc;
        this.jdbcEnabled = jdbcEnabled;
    }

    @PostConstruct
    public void load() {
        if (!jdbcAvailable()) return;
        jdbc.query("""
                SELECT workspace_id, project_id, service_id, repository_id, environment, base_commit,
                       verified_commit, status, summary, unified_diff, changed_files_json,
                       test_profile, test_command, test_exit_code, test_log, artifact_path,
                       artifact_sha256, artifact_size, created_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_repair_workspace
                ORDER BY id DESC
                LIMIT 500
                """, rs -> {
            RepairWorkspace workspace = row(
                    rs.getString("workspace_id"), rs.getString("project_id"), rs.getString("service_id"),
                    rs.getString("repository_id"), rs.getString("environment"), rs.getString("base_commit"),
                    rs.getString("verified_commit"), rs.getString("status"), rs.getString("summary"),
                    rs.getString("unified_diff"), rs.getString("changed_files_json"),
                    rs.getString("test_profile"), rs.getString("test_command"),
                    nullableInteger(rs.getObject("test_exit_code")), rs.getString("test_log"),
                    rs.getString("artifact_path"), rs.getString("artifact_sha256"),
                    nullableLong(rs.getObject("artifact_size")), rs.getString("created_by"),
                    rs.getString("create_time_text"), rs.getString("update_time_text"));
            memory.put(workspace.workspaceId(), workspace);
        });
    }

    @Override
    public RepairWorkspace save(RepairWorkspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
        memory.put(workspace.workspaceId(), workspace);
        if (!jdbcAvailable()) return workspace;
        jdbc.update("""
                INSERT INTO ai_ops_repair_workspace
                (workspace_id, project_id, service_id, repository_id, environment, base_commit,
                 verified_commit, status, summary, unified_diff, changed_files_json,
                 test_profile, test_command, test_exit_code, test_log, artifact_path,
                 artifact_sha256, artifact_size, created_by, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  project_id=VALUES(project_id), service_id=VALUES(service_id),
                  repository_id=VALUES(repository_id), environment=VALUES(environment),
                  base_commit=VALUES(base_commit), verified_commit=VALUES(verified_commit),
                  status=VALUES(status), summary=VALUES(summary), unified_diff=VALUES(unified_diff),
                  changed_files_json=VALUES(changed_files_json), test_profile=VALUES(test_profile),
                  test_command=VALUES(test_command), test_exit_code=VALUES(test_exit_code),
                  test_log=VALUES(test_log), artifact_path=VALUES(artifact_path),
                  artifact_sha256=VALUES(artifact_sha256), artifact_size=VALUES(artifact_size),
                  update_time=VALUES(update_time)
                """, args(workspace));
        return workspace;
    }

    @Override
    public Optional<RepairWorkspace> find(String workspaceId) {
        return Optional.ofNullable(memory.get(value(workspaceId)));
    }

    @Override
    public List<RepairWorkspace> list(String projectId) {
        String project = value(projectId);
        return memory.values().stream()
                .filter(item -> project.isBlank() || project.equals(item.projectId()))
                .sorted(Comparator.comparing(RepairWorkspace::createdAt).reversed())
                .toList();
    }

    @Override
    public RepairWriterLease claimWriter(String workspaceId, String ownerId, long leaseSeconds) {
        requireWriterStore();
        String workspace = required(workspaceId, "workspaceId");
        String owner = required(ownerId, "writer owner");
        Timestamp expiresAt = Timestamp.valueOf(LocalDateTime.now().plusSeconds(Math.max(30L, leaseSeconds)));
        String renewSql = ("""
                UPDATE ai_ops_repair_workspace
                SET %s=?, state_version=state_version+1, update_time=CURRENT_TIMESTAMP
                WHERE workspace_id=? AND %s=?
                  AND %s>CURRENT_TIMESTAMP(3)
                  AND status IN ('ACTIVE','DIRTY','COMMITTED','TESTING','TEST_PASSED','FAILED')
                """).formatted(WRITER_EXPIRES, WRITER_OWNER, WRITER_EXPIRES);
        int renewed = jdbc.update(renewSql, expiresAt, workspace, owner);
        if (renewed == 0) {
            String claimId = UUID.randomUUID().toString();
            String claimSql = ("""
                    UPDATE ai_ops_repair_workspace
                    SET %s=?, %s=?, %s=%s+1, %s=?,
                        state_version=state_version+1, update_time=CURRENT_TIMESTAMP
                    WHERE workspace_id=?
                      AND status IN ('ACTIVE','DIRTY','COMMITTED','TESTING','TEST_PASSED','FAILED')
                      AND (%s='' OR %s IS NULL OR %s<=CURRENT_TIMESTAMP(3))
                    """).formatted(
                    WRITER_OWNER, WRITER_CLAIM, WRITER_FENCE, WRITER_FENCE, WRITER_EXPIRES,
                    WRITER_OWNER, WRITER_EXPIRES, WRITER_EXPIRES);
            int claimed = jdbc.update(claimSql, owner, claimId, expiresAt, workspace);
            if (claimed != 1) {
                throw new IllegalStateException("REPAIR_WORKSPACE_WRITER_BUSY：该工作区正由其他 Run 修改");
            }
        }
        String readSql = ("""
                SELECT project_id, %s, %s, %s, %s, state_version
                FROM ai_ops_repair_workspace
                WHERE workspace_id=? AND %s=?
                LIMIT 1
                """).formatted(
                WRITER_OWNER, WRITER_CLAIM, WRITER_FENCE, WRITER_EXPIRES, WRITER_OWNER);
        List<Map<String, Object>> rows = jdbc.queryForList(readSql, workspace, owner);
        if (rows.isEmpty()) throw new IllegalStateException("REPAIR_WORKSPACE_WRITER_CLAIM_LOST");
        Map<String, Object> row = rows.get(0);
        return new RepairWriterLease(
                workspace,
                String.valueOf(row.get("project_id")),
                owner,
                String.valueOf(row.get(WRITER_CLAIM)),
                number(row.get(WRITER_FENCE)),
                String.valueOf(row.get(WRITER_EXPIRES)),
                number(row.get("state_version")));
    }

    @Override
    public void updateWriterOwnedStatus(String workspaceId, String ownerId, RepairWorkspaceStatus status) {
        requireWriterStore();
        String sql = ("""
                UPDATE ai_ops_repair_workspace
                SET status=?, state_version=state_version+1, update_time=CURRENT_TIMESTAMP
                WHERE workspace_id=? AND %s=? AND %s>CURRENT_TIMESTAMP(3)
                """).formatted(WRITER_OWNER, WRITER_EXPIRES);
        String workspace = required(workspaceId, "workspaceId");
        int updated = jdbc.update(sql, status.name(), workspace, required(ownerId, "writer owner"));
        if (updated != 1) throw new IllegalStateException("REPAIR_WORKSPACE_WRITER_LEASE_LOST");
        memory.computeIfPresent(workspace, (key, value) -> value.withStatus(status, now()));
    }

    @Override
    public boolean releaseWriter(String workspaceId, String ownerId) {
        requireWriterStore();
        String sql = ("""
                UPDATE ai_ops_repair_workspace
                SET %s='', %s='', %s=NULL,
                    state_version=state_version+1, update_time=CURRENT_TIMESTAMP
                WHERE workspace_id=? AND %s=?
                """).formatted(WRITER_OWNER, WRITER_CLAIM, WRITER_EXPIRES, WRITER_OWNER);
        return jdbc.update(
                sql, required(workspaceId, "workspaceId"), required(ownerId, "writer owner")) == 1;
    }

    @Override
    public RepairWorkspace update(RepairWorkspace workspace) {
        return save(workspace);
    }

    private Object[] args(RepairWorkspace workspace) {
        return new Object[]{
                workspace.workspaceId(), workspace.projectId(), workspace.serviceId(), workspace.repositoryId(),
                workspace.environment(), workspace.baseCommit(), workspace.verifiedCommit(), workspace.status().name(),
                workspace.summary(), workspace.unifiedDiff(), JSON.toJSONString(workspace.changedFiles()),
                workspace.testProfile(), workspace.testCommand(), workspace.testExitCode(), workspace.testLog(),
                workspace.artifactPath(), workspace.artifactSha256(), workspace.artifactSize(), workspace.createdBy(),
                workspace.createdAt(), workspace.updatedAt()
        };
    }

    private RepairWorkspace row(
            String workspaceId, String projectId, String serviceId, String repositoryId,
            String environment, String baseCommit, String verifiedCommit, String status,
            String summary, String unifiedDiff, String changedFilesJson, String testProfile,
            String testCommand, Integer testExitCode, String testLog, String artifactPath,
            String artifactSha256, Long artifactSize, String createdBy, String createdAt, String updatedAt) {
        List<String> files = StringUtils.hasText(changedFilesJson)
                ? Optional.ofNullable(JSON.parseArray(changedFilesJson, String.class)).orElse(List.of())
                : List.of();
        return new RepairWorkspace(
                workspaceId, projectId, serviceId, repositoryId, environment, baseCommit, verifiedCommit,
                RepairWorkspaceStatus.require(status), summary, unifiedDiff, files, testProfile, testCommand,
                testExitCode, testLog, artifactPath, artifactSha256, artifactSize, createdBy, createdAt, updatedAt);
    }

    private void requireWriterStore() {
        if (!jdbcAvailable()) throw new IllegalStateException("REPAIR_WORKSPACE_WRITER_STORE_UNAVAILABLE");
    }

    private boolean jdbcAvailable() {
        return jdbcEnabled && jdbc != null;
    }

    private Integer nullableInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception ignored) { return 0L; }
    }

    private String required(String value, String field) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private String now() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(LocalDateTime.now());
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
