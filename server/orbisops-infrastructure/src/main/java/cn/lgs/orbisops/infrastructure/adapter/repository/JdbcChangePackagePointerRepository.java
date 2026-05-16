package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingCompletion;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationFailure;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcChangePackagePointerRepository implements IChangePackagePointerRepository {

    private static final String SELECT_POINTER = """
            SELECT package_id, status, version, package_hash, approved_version, approved_package_hash
            FROM ai_ops_change_package
            WHERE package_id=?
            LIMIT 1
            """;
    private static final String ADVANCE_VERSION = """
            UPDATE ai_ops_change_package
            SET status=?, version=?, package_hash=?, %s,
                approved_version=NULL, approved_package_hash=NULL, approved_snapshot_json=NULL,
                approve_by=NULL, approved_at=NULL, update_time=CURRENT_TIMESTAMP
            WHERE package_id=? AND status=? AND version=? AND package_hash=?
            """.formatted(ChangePackageCurrentStateJdbcMapper.assignmentList());

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcChangePackagePointerRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcChangePackagePointerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public Optional<ChangePackagePointer> find(String packageId) {
        requireAvailable();
        return jdbcTemplate.queryForList(SELECT_POINTER, required(packageId)).stream()
                .findFirst()
                .map(this::map);
    }

    @Override
    public boolean compareAndSetStatus(ChangePackagePointer expected, ChangePackageStatus nextStatus) {
        requireAvailable();
        requireExpected(expected);
        if (nextStatus == null) throw new IllegalArgumentException("CHANGE_PACKAGE_NEXT_STATUS_REQUIRED");
        return jdbcTemplate.update("""
                UPDATE ai_ops_change_package
                SET status=?, update_time=CURRENT_TIMESTAMP
                WHERE package_id=? AND status=? AND version=? AND package_hash=?
                """, nextStatus.name(), expected.packageId(), expected.status().name(),
                expected.version(), expected.packageHash()) == 1;
    }

    @Override
    public boolean compareAndSetVersion(ChangePackagePointer expected,
                                        ChangePackagePointer next,
                                        ChangePackageCurrentState nextState) {
        requireAvailable();
        requireExpected(expected);
        requireVersionAdvance(expected, next, nextState);
        List<Object> values = new ArrayList<>();
        values.add(next.status().name());
        values.add(next.version());
        values.add(next.packageHash());
        values.addAll(ChangePackageCurrentStateJdbcMapper.orderedValues(nextState));
        values.add(expected.packageId());
        values.add(expected.status().name());
        values.add(expected.version());
        values.add(expected.packageHash());
        return jdbcTemplate.update(ADVANCE_VERSION, values.toArray()) == 1;
    }

    @Override
    public boolean compareAndSetValidationFailure(ChangePackagePointer expected,
                                                  ChangePackageValidationFailure failure) {
        requireAvailable();
        requireExpected(expected);
        if (failure == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_FAILURE_REQUIRED");
        }
        return jdbcTemplate.update("""
                UPDATE ai_ops_change_package
                SET status=?, validation_assessment=?, reason_code=?, failure_summary_json=?,
                    update_time=CURRENT_TIMESTAMP
                WHERE package_id=? AND status=? AND version=? AND package_hash=?
                """, failure.status().name(), failure.assessment(), failure.reasonCode(),
                ChangePackageJsonMapCodec.encode(failure.failureSummary()),
                expected.packageId(), expected.status().name(),
                expected.version(), expected.packageHash()) == 1;
    }

    @Override
    public boolean compareAndSetApproved(ChangePackagePointer expected,
                                         ChangePackageSnapshot approvedSnapshot,
                                         String actor) {
        requireAvailable();
        requireExpected(expected);
        if (expected.status() != ChangePackageStatus.REVIEWING) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_EXPECTED_STATUS_INVALID");
        }
        return jdbcTemplate.update("""
                UPDATE ai_ops_change_package
                SET status=?, approved_version=?, approved_package_hash=?, approved_snapshot_json=?,
                    approve_by=?, approved_at=CURRENT_TIMESTAMP, update_time=CURRENT_TIMESTAMP
                WHERE package_id=? AND status=? AND version=? AND package_hash=?
                """, ChangePackageStatus.APPROVED.name(), expected.version(), expected.packageHash(),
                ChangePackageSnapshotJsonCodec.encode(approvedSnapshot), text(actor),
                expected.packageId(), expected.status().name(), expected.version(), expected.packageHash()) == 1;
    }

    @Override
    public boolean compareAndSetLandingStarted(ChangePackagePointer expected, String landingRunId) {
        requireAvailable();
        requireExpected(expected);
        if ((expected.status() != ChangePackageStatus.APPROVED
                && expected.status() != ChangePackageStatus.LANDING_FAILED)
                || !expected.approved()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_POINTER_NOT_APPROVED");
        }
        return jdbcTemplate.update("""
                UPDATE ai_ops_change_package
                SET status=?, landing_run_id=?, update_time=CURRENT_TIMESTAMP
                WHERE package_id=? AND status=? AND version=? AND package_hash=?
                  AND approved_version=? AND approved_package_hash=?
                """, ChangePackageStatus.LANDING_RUNNING.name(), required(landingRunId),
                expected.packageId(), expected.status().name(), expected.version(), expected.packageHash(),
                expected.approvedVersion(), expected.approvedPackageHash()) == 1;
    }

    @Override
    public boolean compareAndSetLandingResult(ChangePackagePointer expected,
                                              ChangePackageLandingCompletion completion) {
        requireAvailable();
        requireExpected(expected);
        if (!expected.approved()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_POINTER_NOT_APPROVED");
        }
        if (completion == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_COMPLETION_REQUIRED");
        }
        return jdbcTemplate.update("""
                UPDATE ai_ops_change_package
                SET status=?, landing_result_json=?, landing_run_id=?, failure_summary_json=?,
                    update_time=CURRENT_TIMESTAMP
                WHERE package_id=? AND status=? AND version=? AND package_hash=?
                  AND approved_version=? AND approved_package_hash=?
                """, completion.status().name(), ChangePackageJsonMapCodec.encode(completion.result()),
                completion.landingRunId(), ChangePackageJsonMapCodec.encode(completion.failureSummary()),
                expected.packageId(), expected.status().name(), expected.version(), expected.packageHash(),
                expected.approvedVersion(), expected.approvedPackageHash()) == 1;
    }

    private ChangePackagePointer map(Map<String, Object> row) {
        return new ChangePackagePointer(
                text(row.get("package_id")),
                ChangePackageStatus.require(text(row.get("status"))),
                intValue(row.get("version")),
                text(row.get("package_hash")),
                intValue(row.get("approved_version")),
                text(row.get("approved_package_hash")));
    }

    private void requireVersionAdvance(ChangePackagePointer expected,
                                       ChangePackagePointer next,
                                       ChangePackageCurrentState nextState) {
        if (next == null) throw new IllegalArgumentException("CHANGE_PACKAGE_NEXT_POINTER_REQUIRED");
        if (nextState == null) throw new IllegalArgumentException("CHANGE_PACKAGE_NEXT_STATE_REQUIRED");
        if (!expected.packageId().equals(next.packageId()) || next.version() != expected.version() + 1) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_ADVANCE_INVALID");
        }
        if (next.approved()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_ADVANCE_MUST_CLEAR_APPROVAL");
        }
    }

    private void requireExpected(ChangePackagePointer expected) {
        if (expected == null) throw new IllegalArgumentException("CHANGE_PACKAGE_EXPECTED_POINTER_REQUIRED");
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_POINTER_STORE_UNAVAILABLE");
    }

    private String required(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_VALUE_REQUIRED");
        return normalized;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
