package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMemberRepository;
import cn.lgs.orbisops.domain.project.model.ProjectMember;
import cn.lgs.orbisops.domain.project.model.ProjectMemberIdentity;
import cn.lgs.orbisops.domain.project.model.ProjectMemberStatus;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
public class JdbcProjectMemberRepository implements IProjectMemberRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.project-workspace.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.project-workspace.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcProjectMemberRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<ProjectMember> list(String projectId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureTable(template);
            return template.query("""
                            SELECT id, project_id, member_key, user_id, username,
                                   member_role, status, granted_by, create_time, update_time
                            FROM ai_ops_project_member
                            WHERE project_id = ?
                            ORDER BY username ASC, user_id ASC
                            """,
                    this::member,
                    projectId);
        } catch (DataAccessException error) {
            log.warn("查询项目成员失败 projectId={} reason={}", projectId, error.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<ProjectMember> findEnabled(String projectId,
                                               ProjectMemberIdentity identity) {
        JdbcTemplate template = availableTemplate();
        List<String> keys = identity == null ? List.of() : identity.keys();
        if (template == null || keys.isEmpty()) {
            return Optional.empty();
        }
        try {
            ensureTable(template);
            String placeholders = placeholders(keys.size());
            List<Object> args = new ArrayList<>();
            args.add(projectId);
            addRepeated(args, keys, 3);
            List<ProjectMember> members = template.query("""
                            SELECT id, project_id, member_key, user_id, username,
                                   member_role, status, granted_by, create_time, update_time
                            FROM ai_ops_project_member
                            WHERE project_id = ?
                              AND status = 'ENABLED'
                              AND (member_key IN (%s) OR user_id IN (%s) OR username IN (%s))
                            ORDER BY id DESC
                            LIMIT 1
                            """.formatted(placeholders, placeholders, placeholders),
                    this::member,
                    args.toArray());
            return members.stream().findFirst();
        } catch (DataAccessException error) {
            log.warn("查询项目成员角色失败 projectId={} reason={}", projectId, error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Set<String> findEnabledProjectIds(ProjectMemberIdentity identity) {
        JdbcTemplate template = availableTemplate();
        List<String> keys = identity == null ? List.of() : identity.keys();
        if (template == null || keys.isEmpty()) {
            return Set.of();
        }
        try {
            ensureTable(template);
            String placeholders = placeholders(keys.size());
            List<Object> args = new ArrayList<>();
            addRepeated(args, keys, 3);
            return new LinkedHashSet<>(template.queryForList("""
                            SELECT DISTINCT project_id
                            FROM ai_ops_project_member
                            WHERE status = 'ENABLED'
                              AND (member_key IN (%s) OR user_id IN (%s) OR username IN (%s))
                            """.formatted(placeholders, placeholders, placeholders),
                    String.class,
                    args.toArray()));
        } catch (DataAccessException error) {
            log.warn("查询成员项目授权失败 reason={}", error.getMessage());
            return Set.of();
        }
    }

    @Override
    public ProjectMember grantIfAbsent(ProjectMember member) {
        if (member == null) {
            throw new IllegalArgumentException("PROJECT_MEMBER_REQUIRED");
        }
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        template.update("""
                        INSERT INTO ai_ops_project_member
                        (project_id, member_key, user_id, username, member_role, status, granted_by)
                        VALUES (?, ?, ?, ?, ?, 'ENABLED', ?)
                        ON DUPLICATE KEY UPDATE
                          user_id = VALUES(user_id),
                          username = VALUES(username),
                          member_role = IF(status = 'ENABLED', member_role, VALUES(member_role)),
                          status = 'ENABLED',
                          granted_by = IF(granted_by = '', VALUES(granted_by), granted_by),
                          update_time = CURRENT_TIMESTAMP
                        """,
                member.projectId(),
                member.memberKey(),
                member.userId(),
                member.username(),
                member.role().name(),
                member.grantedBy());
        return findEnabled(member.projectId(),
                new ProjectMemberIdentity(member.userId(), member.username()))
                .orElse(member);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public List<ProjectMember> replace(String projectId, List<ProjectMember> members) {
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        List<ProjectMember> commands = members == null ? List.of() : List.copyOf(members);
        template.update("DELETE FROM ai_ops_project_member WHERE project_id = ?", projectId);
        for (ProjectMember member : commands) {
            template.update("""
                            INSERT INTO ai_ops_project_member
                            (project_id, member_key, user_id, username, member_role, status, granted_by)
                            VALUES (?, ?, ?, ?, ?, 'ENABLED', ?)
                            """,
                    projectId,
                    member.memberKey(),
                    member.userId(),
                    member.username(),
                    member.role().name(),
                    member.grantedBy());
        }
        return list(projectId);
    }

    private ProjectMember member(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProjectMember(
                resultSet.getLong("id"),
                resultSet.getString("project_id"),
                resultSet.getString("member_key"),
                resultSet.getString("user_id"),
                resultSet.getString("username"),
                ProjectRole.parse(resultSet.getString("member_role")),
                ProjectMemberStatus.parse(resultSet.getString("status")),
                resultSet.getString("granted_by"),
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            throw new IllegalStateException("项目成员授权需要 MySQL 持久化");
        }
        return template;
    }

    private JdbcTemplate availableTemplate() {
        return jdbcEnabled ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized || !autoInit) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_project_member (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      project_id VARCHAR(80) NOT NULL COMMENT '项目ID',
                      member_key VARCHAR(128) NOT NULL COMMENT '稳定成员键，优先userId',
                      user_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户ID',
                      username VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户名',
                      member_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色',
                      status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '授权状态',
                      granted_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '授权人',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_project_member (project_id, member_key),
                      KEY idx_member_user (user_id, status),
                      KEY idx_member_name (username, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维项目成员授权表'
                    """);
            initialized = true;
        }
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private void addRepeated(List<Object> args, List<String> keys, int repeats) {
        for (int index = 0; index < repeats; index++) {
            args.addAll(keys);
        }
    }

    private LocalDateTime time(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
