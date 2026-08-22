package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.repair.adapter.repository.ICodeDeliveryRepository;
import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@DependsOn("jdbcCodeDeliverySchemaInitializer")
public class JdbcCodeDeliveryRepository implements ICodeDeliveryRepository {

    private final JdbcTemplate jdbc;
    private final boolean jdbcEnabled;
    private final Map<String, CodeDelivery> memory = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcCodeDeliveryRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${orbisops.repair.jdbc-enabled:true}") boolean jdbcEnabled) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.jdbcEnabled = jdbcEnabled;
    }

    JdbcCodeDeliveryRepository(JdbcTemplate jdbc, boolean jdbcEnabled) {
        this.jdbc = jdbc;
        this.jdbcEnabled = jdbcEnabled;
    }

    @PostConstruct
    public void load() {
        if (!jdbcAvailable()) return;
        jdbc.query("""
                        SELECT delivery_id, workspace_id, project_id, service_id, delivery_mode, branch_name,
                               commit_sha, pull_request_url, ci_status, ci_url, created_by,
                               DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                               DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                        FROM ai_ops_code_delivery
                        ORDER BY id DESC
                        LIMIT 500
                        """,
                rs -> {
                    CodeDelivery delivery = new CodeDelivery(
                            rs.getString("delivery_id"), rs.getString("workspace_id"),
                            rs.getString("project_id"), rs.getString("service_id"),
                            CodeDeliveryMode.require(rs.getString("delivery_mode")),
                            rs.getString("branch_name"), rs.getString("commit_sha"),
                            rs.getString("pull_request_url"), rs.getString("ci_status"),
                            rs.getString("ci_url"), rs.getString("created_by"),
                            rs.getString("create_time_text"), rs.getString("update_time_text"));
                    memory.put(delivery.deliveryId(), delivery);
                });
    }

    @Override
    public CodeDelivery save(CodeDelivery delivery) {
        if (delivery == null) throw new IllegalArgumentException("CODE_DELIVERY_REQUIRED");
        if (jdbcAvailable()) {
            jdbc.update("""
                            INSERT INTO ai_ops_code_delivery
                            (delivery_id, workspace_id, project_id, service_id, delivery_mode, branch_name,
                             commit_sha, pull_request_url, ci_status, ci_url, created_by, create_time, update_time)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              pull_request_url=VALUES(pull_request_url), ci_status=VALUES(ci_status),
                              ci_url=VALUES(ci_url), update_time=VALUES(update_time)
                            """,
                    delivery.deliveryId(), delivery.workspaceId(), delivery.projectId(), delivery.serviceId(),
                    delivery.mode().name(), delivery.branchName(), delivery.commitSha(),
                    delivery.pullRequestUrl(), delivery.ciStatus(), delivery.ciUrl(), delivery.createdBy(),
                    delivery.createdAt(), delivery.updatedAt());
        }
        memory.put(delivery.deliveryId(), delivery);
        return delivery;
    }

    @Override
    public Optional<CodeDelivery> find(String deliveryId) {
        return Optional.ofNullable(memory.get(value(deliveryId)));
    }

    @Override
    public Optional<CodeDelivery> findByWorkspaceAndMode(String workspaceId, CodeDeliveryMode mode) {
        String workspace = value(workspaceId);
        if (workspace.isBlank() || mode == null) return Optional.empty();
        return memory.values().stream()
                .filter(item -> workspace.equals(item.workspaceId()))
                .filter(item -> mode == item.mode())
                .findFirst();
    }

    @Override
    public List<CodeDelivery> list(String workspaceId) {
        String workspace = value(workspaceId);
        return memory.values().stream()
                .filter(item -> workspace.isBlank() || workspace.equals(item.workspaceId()))
                .sorted(Comparator.comparing(CodeDelivery::createdAt).reversed())
                .toList();
    }

    private boolean jdbcAvailable() {
        return jdbcEnabled && jdbc != null;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
