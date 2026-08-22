package cn.lgs.orbisops.trigger.application.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsChannelInboundProtocolConfiguration {

    @Bean
    public OpsChannelInboundProtocolSettings opsChannelInboundProtocolSettings(
            @Value("${orbisops.channel.webhook.max-clock-skew-seconds:300}") long maxClockSkewSeconds,
            @Value("${orbisops.channel.inbound.max-message-chars:12000}") int maxMessageChars,
            @Value("${orbisops.channel.inbound.max-metadata-chars:8192}") int maxMetadataChars) {
        return new OpsChannelInboundProtocolSettings(
                maxClockSkewSeconds,
                maxMessageChars,
                maxMetadataChars);
    }
}
