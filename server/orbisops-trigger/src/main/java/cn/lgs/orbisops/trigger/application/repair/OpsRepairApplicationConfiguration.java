package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.CodeDeliveryApplicationService;
import cn.lgs.orbisops.application.repair.CodeDeliveryGitPort;
import cn.lgs.orbisops.application.repair.CodeDeliveryProviderPort;
import cn.lgs.orbisops.application.repair.ControlledCodeApplicationService;
import cn.lgs.orbisops.application.repair.ControlledCodeAuditPort;
import cn.lgs.orbisops.application.repair.ControlledCodeFilePort;
import cn.lgs.orbisops.application.repair.ControlledCodeProofPort;
import cn.lgs.orbisops.application.repair.ControlledCodeRemotePort;
import cn.lgs.orbisops.application.repair.ControlledCodeSourcePort;
import cn.lgs.orbisops.application.repair.ControlledCodeToolResultPort;
import cn.lgs.orbisops.application.repair.RepairAuditPort;
import cn.lgs.orbisops.application.repair.RepairSourceCatalogPort;
import cn.lgs.orbisops.application.repair.RepairTransactionPort;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.application.repair.RepairWorkspaceExecutionPort;
import cn.lgs.orbisops.domain.repair.adapter.repository.ICodeDeliveryRepository;
import cn.lgs.orbisops.domain.repair.adapter.repository.IRepairWorkspaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class OpsRepairApplicationConfiguration {

    @Bean
    public RepairWorkspaceApplicationService repairWorkspaceApplicationService(
            IRepairWorkspaceRepository workspaces,
            RepairSourceCatalogPort sources,
            RepairWorkspaceExecutionPort execution,
            RepairAuditPort audit,
            RepairTransactionPort transactions,
            @Value("${orbisops.repair.enabled:false}") boolean enabled,
            @Value("${orbisops.repair.runner:docker}") String runner,
            @Value("${orbisops.repair.writer-lease-seconds:300}") long writerLeaseSeconds) {
        return new RepairWorkspaceApplicationService(
                workspaces, sources, execution, audit, transactions,
                enabled, runner, writerLeaseSeconds);
    }

    @Bean
    public CodeDeliveryApplicationService codeDeliveryApplicationService(
            RepairWorkspaceApplicationService workspaces,
            ICodeDeliveryRepository deliveries,
            CodeDeliveryGitPort git,
            CodeDeliveryProviderPort provider,
            RepairAuditPort audit,
            RepairTransactionPort transactions) {
        return new CodeDeliveryApplicationService(
                workspaces,
                deliveries,
                git,
                provider,
                () -> "delivery_" + UUID.randomUUID().toString().replace("-", ""),
                Clock.systemDefaultZone(),
                audit,
                transactions);
    }

    @Bean
    public ControlledCodeApplicationService controlledCodeApplicationService(
            ControlledCodeSourcePort sources,
            RepairWorkspaceApplicationService workspaces,
            ControlledCodeFilePort files,
            ControlledCodeRemotePort remote,
            ControlledCodeAuditPort audit,
            ControlledCodeToolResultPort toolResults,
            ControlledCodeProofPort proofs) {
        return new ControlledCodeApplicationService(
                sources, workspaces, files, remote, audit, toolResults, proofs);
    }
}
