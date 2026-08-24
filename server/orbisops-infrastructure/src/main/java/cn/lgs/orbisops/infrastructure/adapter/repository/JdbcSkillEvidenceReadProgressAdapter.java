package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillEvidenceReadProgressPort;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;

/** Append-only navigation checkpoints for exact frozen inputs; contents are rebuilt and rechecked. */
@Repository
public class JdbcSkillEvidenceReadProgressAdapter implements SkillEvidenceReadProgressPort {
    private final JdbcTemplate jdbc;

    public JdbcSkillEvidenceReadProgressAdapter(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbc = provider.getIfAvailable();
    }

    private void require(String key, String sourceHash) {
        if (key == null || !key.matches("[0-9a-f]{64}") || sourceHash == null || !sourceHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid evidence checkpoint identity");
        if (jdbc == null) throw new IllegalStateException("SKILL_EVIDENCE_PROGRESS_UNAVAILABLE");
    }

    @Override public List<String> read(String key, String sourceHash) {
        require(key, sourceHash);
        var rows = jdbc.queryForList("SELECT source_hash,read_refs_json FROM ai_ops_skill_evidence_read_progress WHERE progress_key=?", key);
        if (rows.isEmpty()) return List.of();
        var row = rows.get(0);
        if (!sourceHash.equals(row.get("source_hash"))) throw new SecurityException("Evidence checkpoint source mismatch");
        return List.copyOf(JSON.parseArray(row.get("read_refs_json").toString(), String.class));
    }

    @Override
    @Transactional(transactionManager="mysqlTransactionManager", propagation=Propagation.REQUIRES_NEW, timeout=3)
    public void append(String key, String sourceHash, List<String> references) {
        require(key, sourceHash);
        if (references == null || references.size() > 4096 || references.stream().anyMatch(id -> id == null || !id.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Invalid evidence checkpoint references");
        // A duplicate INSERT IGNORE retains a shared record lock. Acquiring the write lock
        // here avoids competing shared-to-exclusive upgrades during first concurrent appends.
        jdbc.update("INSERT INTO ai_ops_skill_evidence_read_progress(progress_key,source_hash,read_refs_json,read_count) VALUES(?,?,'[]',0) ON DUPLICATE KEY UPDATE progress_key=VALUES(progress_key)", key, sourceHash);
        jdbc.queryForObject("SELECT progress_key FROM ai_ops_skill_evidence_read_progress WHERE progress_key=? FOR UPDATE", String.class, key);
        var merged = new LinkedHashSet<>(read(key, sourceHash));
        merged.addAll(references);
        if (merged.size() > 4096) throw new IllegalArgumentException("Evidence checkpoint reference budget exhausted");
        jdbc.update("UPDATE ai_ops_skill_evidence_read_progress SET read_refs_json=?,read_count=?,row_version=row_version+1,update_time=CURRENT_TIMESTAMP WHERE progress_key=? AND source_hash=?",
            CanonicalJson.stringify(merged.stream().toList()), merged.size(), key, sourceHash);
    }
}
