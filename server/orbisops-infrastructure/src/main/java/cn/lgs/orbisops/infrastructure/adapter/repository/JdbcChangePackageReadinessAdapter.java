package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingJournalPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessSnapshot;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageApprovalRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcChangePackageReadinessAdapter implements ChangePackageReadinessPort {

    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageVersionRepository versionRepository;
    private final IChangePackageEventRepository eventRepository;
    private final IChangePackageApprovalRepository approvalRepository;
    private final ChangePackageLandingJournalPort journalPort;
    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.change-package.auto-init:true}")
    private boolean autoInit;

    @Value("${orbisops.approved-landing.enabled:false}")
    private boolean approvedLandingEnabled;

    public JdbcChangePackageReadinessAdapter(IChangePackageCurrentRepository currentRepository,
                                             IChangePackageVersionRepository versionRepository,
                                             IChangePackageEventRepository eventRepository,
                                             IChangePackageApprovalRepository approvalRepository,
                                             ChangePackageLandingJournalPort journalPort,
                                             @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.currentRepository = currentRepository;
        this.versionRepository = versionRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.journalPort = journalPort;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public ChangePackageReadinessSnapshot readiness() {
        requireAvailable();
        try {
            currentRepository.verifyReadable();
            List<String> tables = List.of(
                    "ai_ops_change_package_version",
                    "ai_ops_change_package_event",
                    "ai_ops_change_package_landing_run",
                    "ai_ops_change_package_landing_operation_run",
                    "ai_ops_change_resource_lock",
                    "ai_ops_change_package_approval_record");
            for (String table : tables) {
                jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table, Integer.class);
            }
            return ChangePackageReadinessSnapshot.up("ChangePackageStore", autoInit);
        } catch (DataAccessException error) {
            throw new IllegalStateException("ChangePackageStore readiness 检查失败：" + error.getMessage(), error);
        }
    }

    @Override
    public boolean approvedLandingEnabled() {
        return approvedLandingEnabled;
    }

    @Override
    public boolean operationJournalReady() {
        return journalPort != null && journalPort.available();
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_STORE_UNAVAILABLE");
        if (!currentRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
        if (!versionRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_VERSION_STORE_UNAVAILABLE");
        if (!eventRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_EVENT_STORE_UNAVAILABLE");
        if (!approvalRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_STORE_UNAVAILABLE");
    }
}
