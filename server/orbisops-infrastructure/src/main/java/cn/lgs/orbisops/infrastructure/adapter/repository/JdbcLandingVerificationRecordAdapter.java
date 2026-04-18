package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;

@Repository
public class JdbcLandingVerificationRecordAdapter implements LandingVerificationRecordPort {
    private final JdbcTemplate jdbc;

    public JdbcLandingVerificationRecordAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        jdbc = provider.getIfAvailable();
    }

    @Override
    public void record(String id, String runId, String projectId, String packageId,
                       long version, String hash, boolean passed, Map<String, Object> proof) {
        if (jdbc == null) throw new IllegalStateException("LANDING_VERIFICATION_STORE_UNAVAILABLE");
        int inserted = jdbc.update("""
                INSERT INTO ai_ops_landing_verification
                    (verification_id, landing_run_id, project_id, package_id,
                     approved_version, approved_package_hash, passed, proof_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, runId, projectId, packageId, version, hash, passed, JSON.toJSONString(proof));
        if (inserted != 1) throw new IllegalStateException("LANDING_VERIFICATION_NOT_RECORDED");
    }
}
