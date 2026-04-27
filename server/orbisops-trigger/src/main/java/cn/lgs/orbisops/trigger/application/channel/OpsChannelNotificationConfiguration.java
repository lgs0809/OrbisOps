package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsChannelNotificationConfiguration {

    @Bean
    public OpsChannelNotificationSettings opsChannelNotificationSettings(
            @Value("${orbisops.channel.notification.max-message-chars:12000}") int maxMessageChars,
            @Value("${orbisops.channel.notification.outbox.max-attempts:8}") int maxAttempts,
            @Value("${orbisops.channel.notification.outbox.lease-seconds:120}") int leaseSeconds) {
        return new OpsChannelNotificationSettings(maxMessageChars, maxAttempts, leaseSeconds);
    }
}
