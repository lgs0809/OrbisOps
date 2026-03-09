package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelNotificationDeliveryPort;
import cn.lgs.orbisops.application.channel.ChannelNotificationUseCase;
import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring assembly for durable Channel notification and outbox administration. */
@Configuration
public class OpsChannelNotificationAssembly {

    @Bean
    public OpsChannelOutboxDispatcher opsChannelOutboxDispatcher(
            IChannelOutboxRepository repository,
            ChannelChatProcessManager channelChatProcessManager,
            ChannelOutboundContentPolicy contentPolicy) {
        return new OpsChannelOutboxDispatcher(repository, channelChatProcessManager, contentPolicy);
    }

    @Bean
    public OpsChannelOutboxViewMapper opsChannelOutboxViewMapper() {
        return new OpsChannelOutboxViewMapper();
    }

    @Bean
    public OpsChannelOutboxRecordFactory opsChannelOutboxRecordFactory(
            ChannelOutboundContentPolicy contentPolicy,
            OpsChannelNotificationSettings settings) {
        return new OpsChannelOutboxRecordFactory(contentPolicy, settings);
    }

    @Bean
    public OpsChannelOutboxEnqueueService opsChannelOutboxEnqueueService(
            IChannelOutboxRepository repository,
            ChannelQueryService channelQueryService,
            OpsChannelOutboxRecordFactory recordFactory,
            OpsConfigAuditService auditService) {
        return new OpsChannelOutboxEnqueueService(
                repository,
                channelQueryService,
                recordFactory,
                auditService);
    }

    @Bean
    public OpsChannelOutboxQueryService opsChannelOutboxQueryService(
            IChannelOutboxRepository repository,
            ChannelQueryService channelQueryService,
            OpsChannelOutboxViewMapper viewMapper) {
        return new OpsChannelOutboxQueryService(repository, channelQueryService, viewMapper);
    }

    @Bean
    public OpsChannelOutboxManagementService opsChannelOutboxManagementService(
            IChannelOutboxRepository repository,
            OpsChannelOutboxQueryService queryService,
            OpsConfigAuditService auditService) {
        return new OpsChannelOutboxManagementService(repository, queryService, auditService);
    }

    @Bean
    public OpsChannelOutboxBatchProcessor opsChannelOutboxBatchProcessor(
            IChannelOutboxRepository repository,
            OpsChannelOutboxDispatcher dispatcher,
            OpsChannelNotificationSettings settings) {
        return new OpsChannelOutboxBatchProcessor(repository, dispatcher, settings);
    }

    @Bean
    public ChannelNotificationDeliveryPort channelNotificationDeliveryPort(
            OpsChannelOutboxEnqueueService enqueueService,
            OpsChannelOutboxDispatcher dispatcher,
            OpsChannelOutboxQueryService queryService,
            ChannelOutboundContentPolicy contentPolicy,
            OpsChannelNotificationSettings settings) {
        return new OpsChannelNotificationDeliveryAdapter(
                enqueueService,
                dispatcher,
                queryService,
                contentPolicy,
                settings);
    }

    @Bean
    public ChannelNotificationUseCase channelNotificationUseCase(
            ChannelNotificationDeliveryPort deliveryPort) {
        return new ChannelNotificationUseCase(deliveryPort);
    }
}
