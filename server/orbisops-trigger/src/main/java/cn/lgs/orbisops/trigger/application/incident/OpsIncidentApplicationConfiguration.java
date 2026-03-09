package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.incident.IncidentAlertApplicationService;
import cn.lgs.orbisops.application.incident.IncidentAuditPort;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.incident.IncidentChangePackageQueryPort;
import cn.lgs.orbisops.application.incident.IncidentDiagnosisQueryPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.incident.IncidentTransactionPort;
import cn.lgs.orbisops.application.incident.IncidentVerificationApplicationService;
import cn.lgs.orbisops.application.incident.IncidentVerificationPort;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class OpsIncidentApplicationConfiguration {

    @Bean
    public IncidentDiagnosisQueryPort incidentDiagnosisQueryPort(
            IAnalysisRunRepository runs,
            OpsAnalysisRunPersistenceMapper persistenceMapper,
            OpsStructuredReportService reports) {
        return new OpsIncidentDiagnosisQueryAdapter(runs, persistenceMapper, reports);
    }

    @Bean
    public IncidentQueryApplicationService incidentQueryApplicationService(
            IIncidentRepository incidents,
            IncidentChangePackageQueryPort changePackages,
            IncidentDiagnosisQueryPort diagnoses) {
        return new IncidentQueryApplicationService(incidents, changePackages, diagnoses);
    }

    @Bean
    public IncidentVerificationPort incidentVerificationPort(ChangePackageQueryService changePackages) {
        return new OpsIncidentChangePackageVerificationAdapter(changePackages);
    }

    @Bean
    public IncidentVerificationApplicationService incidentVerificationApplicationService(
            IIncidentRepository incidents,
            IncidentVerificationPort verification,
            IncidentAuditPort audit,
            IncidentTransactionPort transactions) {
        return new IncidentVerificationApplicationService(incidents, verification, audit, transactions);
    }

    @Bean
    public IncidentCommandApplicationService incidentCommandApplicationService(
            IIncidentRepository incidents,
            IncidentAuditPort audit,
            IncidentTransactionPort transactions) {
        return new IncidentCommandApplicationService(
                incidents,
                audit,
                () -> "incident_" + UUID.randomUUID().toString().replace("-", ""),
                transactions);
    }

    @Bean
    public IncidentAlertApplicationService incidentAlertApplicationService(
            IIncidentRepository incidents,
            IncidentAuditPort audit,
            IncidentTransactionPort transactions) {
        return new IncidentAlertApplicationService(incidents, audit, transactions);
    }
}
