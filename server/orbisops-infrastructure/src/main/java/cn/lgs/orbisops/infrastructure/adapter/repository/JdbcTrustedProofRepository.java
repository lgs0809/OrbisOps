package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.evidence.adapter.repository.ITrustedProofRepository;
import cn.lgs.orbisops.domain.evidence.model.TrustedProof;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofCriteria;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofSource;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofStatus;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@DependsOn("jdbcEvidenceSchemaInitializer")
public class JdbcTrustedProofRepository implements ITrustedProofRepository {

    private final JdbcTemplate jdbc;
    private final boolean memoryFallbackAllowed;
    private final Map<String, TrustedProof> memory = new ConcurrentHashMap<>();

    public JdbcTrustedProofRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            @Value("${orbisops.safety.allow-in-memory-evidence-store:false}") boolean allowMemory,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.jdbc = provider.getIfAvailable();
        this.memoryFallbackAllowed = allowMemory && developmentProfile(activeProfiles);
    }

    @Override
    public TrustedProof save(TrustedProof proof) {
        if (proof == null) throw new IllegalArgumentException("TRUSTED_PROOF_REQUIRED");
        if (jdbc != null) {
            try {
                jdbc.update("""
                        INSERT INTO ai_ops_trusted_proof
                        (proof_id, project_id, package_id, package_version, package_hash, proof_type, source,
                         external_run_id, command_hash, script_hash, result_status, risk_level, metadata_json, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          result_status=VALUES(result_status), metadata_json=VALUES(metadata_json)
                        """,
                        proof.proofId(), proof.projectId(), proof.packageId(), proof.packageVersion(), proof.packageHash(),
                        proof.proofType(), proof.source().name(), proof.externalRunId(), proof.commandHash(),
                        proof.scriptHash(), proof.resultStatus().name(), proof.riskLevel(),
                        JSON.toJSONString(proof.metadata()), proof.createdBy());
                memory.put(proof.proofId(), proof);
                return proof;
            } catch (DataAccessException e) {
                throw new IllegalStateException(
                        "TrustedProof 持久化失败，validation/approval/landing 必须 fail closed：" + e.getMessage(), e);
            }
        }
        if (!memoryFallbackAllowed) {
            throw new IllegalStateException(
                    "TrustedProofService 未配置持久化存储，生产/default 模式禁止内存 fallback");
        }
        memory.put(proof.proofId(), proof);
        return proof;
    }

    @Override
    public Optional<TrustedProof> find(TrustedProofCriteria criteria) {
        if (criteria == null) throw new IllegalArgumentException("TRUSTED_PROOF_CRITERIA_REQUIRED");
        if (jdbc == null) {
            if (!memoryFallbackAllowed) {
                throw new IllegalStateException(
                        "TrustedProofService 未配置持久化存储，生产/default 模式禁止内存 verify fallback");
            }
            return memory.values().stream().filter(item -> item.matches(criteria)).findFirst();
        }
        List<TrustedProof> candidates = jdbc.queryForList("""
                SELECT * FROM ai_ops_trusted_proof
                WHERE project_id=? AND package_id=? AND package_version=? AND package_hash=?
                  AND risk_level=? AND proof_type=?
                  AND result_status IN ('PASSED','SUCCEEDED','SUCCESS')
                  AND source IN ('CI_PROVIDER','PLATFORM_CI','SANDBOX_VERIFIED','APPROVED_VALIDATION_SCRIPT',
                                 'CONTROLLED_BASH_EXECUTED','REPAIR_WORKSPACE_VERIFIED','TOOL_EXECUTED')
                  AND (?='' OR proof_id=? OR external_run_id=?)
                ORDER BY id DESC LIMIT 1
                """,
                criteria.projectId(), criteria.packageId(), criteria.packageVersion(), criteria.packageHash(),
                criteria.riskLevel(), criteria.proofType(), criteria.externalRunIdOrProofId(),
                criteria.externalRunIdOrProofId(), criteria.externalRunIdOrProofId()).stream().map(this::map).toList();
        return candidates.stream().filter(item -> item.matches(criteria)).findFirst();
    }

    @Override
    public long count() {
        if (jdbc != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(1) FROM ai_ops_trusted_proof", Long.class);
            return count == null ? 0L : count;
        }
        if (!memoryFallbackAllowed) throw new IllegalStateException("TrustedProofService 未配置持久化存储");
        return memory.size();
    }

    @Override
    public boolean persistent() {
        return jdbc != null;
    }

    @Override
    public boolean memoryFallbackAllowed() {
        return memoryFallbackAllowed;
    }

    @SuppressWarnings("unchecked")
    private TrustedProof map(Map<String, Object> row) {
        Map<String, Object> metadata;
        try {
            String json = value(row.get("metadata_json"));
            metadata = json.isBlank() ? Map.of() : JSON.parseObject(json, Map.class);
        } catch (Exception ignored) {
            metadata = Map.of();
        }
        return new TrustedProof(
                value(row.get("proof_id")), value(row.get("project_id")), value(row.get("package_id")),
                number(row.get("package_version")).intValue(), value(row.get("package_hash")),
                value(row.get("proof_type")), TrustedProofSource.require(value(row.get("source"))),
                value(row.get("external_run_id")), value(row.get("command_hash")), value(row.get("script_hash")),
                TrustedProofStatus.require(value(row.get("result_status"))), value(row.get("risk_level")),
                metadata, value(row.get("created_by")), time(row.get("create_time")));
    }

    private boolean developmentProfile(String profiles) {
        for (String profile : value(profiles).toLowerCase(Locale.ROOT).split(",")) {
            if ("dev".equals(profile.trim()) || "test".equals(profile.trim())) return true;
        }
        return false;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Number number(Object value) {
        if (value instanceof Number number) return number;
        try { return Long.parseLong(this.value(value)); }
        catch (Exception ignored) { return 0L; }
    }

    private String time(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        String normalized = this.value(value);
        return normalized.isBlank() ? "1970-01-01T00:00:00Z" : normalized;
    }
}
