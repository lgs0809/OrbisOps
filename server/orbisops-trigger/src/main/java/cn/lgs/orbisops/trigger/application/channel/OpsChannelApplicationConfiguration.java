package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelCatalogQuery;
import cn.lgs.orbisops.application.channel.ChannelConversationLeasePort;
import cn.lgs.orbisops.application.channel.ChannelConversationLeaseService;
import cn.lgs.orbisops.application.channel.ChannelIdentityService;
import cn.lgs.orbisops.application.channel.ChannelManagementApplicationService;
import cn.lgs.orbisops.application.channel.ChannelExecutionBindingPort;
import cn.lgs.orbisops.application.channel.ChannelAuditPort;
import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.channel.ChannelInboundProcessManager;
import cn.lgs.orbisops.application.channel.ChannelInboundProtocolPort;
import cn.lgs.orbisops.application.channel.ChannelAgentChatPort;
import cn.lgs.orbisops.application.channel.ChannelReplyOutboxPort;
import cn.lgs.orbisops.application.channel.ChannelTaskExecutorPort;
import cn.lgs.orbisops.application.channel.ChannelRecoveryDispatchPort;
import cn.lgs.orbisops.application.channel.ChannelOutboundApplicationService;
import cn.lgs.orbisops.application.channel.ChannelRunProgressApplicationService;
import cn.lgs.orbisops.application.channel.ChannelOutboundDeliveryPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.application.channel.ChannelProtocolCatalogPort;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionTokenRepositoryPort;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionTokenService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class OpsChannelApplicationConfiguration {

    @Bean
    public ChannelOutboundContentPolicy channelOutboundContentPolicy() {
        return new ChannelOutboundContentPolicy();
    }

    @Bean
    public ChannelApprovalActionTokenService channelApprovalActionTokenService(
            ChannelApprovalActionTokenRepositoryPort repository) {
        return new ChannelApprovalActionTokenService(repository);
    }

    @Bean
    public ChannelConversationLeaseService channelConversationLeaseService(ChannelConversationLeasePort port) {
        return new ChannelConversationLeaseService(port);
    }

    @Bean
    public ChannelManagementApplicationService channelManagementApplicationService(
            IChannelRepository repository,
            ChannelCatalogQuery catalogQuery,
            ChannelProtocolCatalogPort protocolCatalog,
            ChannelExecutionBindingPort executionBindingPort,
            ChannelIdentityDirectoryPort identityDirectoryPort,
            ChannelAuditPort auditPort,
            ChannelRecoveryDispatchPort recoveryDispatchPort) {
        return new ChannelManagementApplicationService(repository, catalogQuery, protocolCatalog,
                executionBindingPort, identityDirectoryPort, auditPort, recoveryDispatchPort);
    }

    @Bean
    public ChannelInboundProcessManager channelInboundProcessManager(
            IChannelRepository repository,
            ChannelInboundProtocolPort protocol,
            ChannelConversationLeaseService leases,
            ChannelExecutionBindingPort executionBindings,
            ChannelIdentityDirectoryPort identities,
            ChannelAgentChatPort chat,
            ChannelReplyOutboxPort replies,
            ChannelRuntimeAuditPort audit,
            ChannelTaskExecutorPort executor,
            ChannelOutboundContentPolicy contentPolicy,
            @Value("${orbisops.channel.inbound.worker.enabled:true}") boolean workerEnabled,
            @Value("${orbisops.channel.inbound.worker.batch-size:20}") int workerBatchSize,
            @Value("${orbisops.channel.inbound.lease-seconds:300}") int leaseSeconds) {
        return new ChannelInboundProcessManager(repository, protocol, leases, executionBindings, identities, chat,
                replies, audit, executor, contentPolicy, workerEnabled, workerBatchSize, leaseSeconds);
    }

    @Bean
    public ReceiveChannelMessageUseCase receiveChannelMessageUseCase(ChannelInboundProcessManager processManager) {
        return new ReceiveChannelMessageUseCase(processManager);
    }

    @Bean
    public ChannelOutboundApplicationService channelOutboundApplicationService(
            IChannelRepository repository,
            ChannelOutboundDeliveryPort deliveryPort,
            ChannelRuntimeAuditPort auditPort,
            ChannelOutboundContentPolicy contentPolicy,
            @Value("${orbisops.channel.outbound.max-message-chars:20000}") int maxMessageChars) {
        return new ChannelOutboundApplicationService(repository, deliveryPort, auditPort,
                contentPolicy, maxMessageChars);
    }

    @Bean
    public ChannelRunProgressApplicationService channelRunProgressApplicationService(
            IChannelRepository repository,
            ChannelOutboundApplicationService outbound,
            ChannelOutboundDeliveryPort deliveryPort) {
        return new ChannelRunProgressApplicationService(repository, outbound, deliveryPort);
    }

    @Bean
    public ChannelChatProcessManager channelChatProcessManager(ChannelOutboundApplicationService outbound) {
        return new ChannelChatProcessManager(outbound);
    }

    @Bean
    public ChannelIdentityService channelIdentityService(ChannelCatalogQuery catalogQuery,
                                                         ChannelManagementApplicationService managementService) {
        return new ChannelIdentityService(catalogQuery, managementService);
    }

    @Bean
    public ChannelQueryService channelQueryService(IChannelRepository repository,
                                                   ChannelProtocolCatalogPort protocolCatalog) {
        return new ChannelQueryService(repository, protocolCatalog);
    }
}
