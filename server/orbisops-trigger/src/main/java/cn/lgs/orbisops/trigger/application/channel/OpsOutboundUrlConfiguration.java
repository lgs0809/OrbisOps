package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.trigger.ops.channel.OpsOutboundUrlSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsOutboundUrlConfiguration {

    @Bean
    public OpsOutboundUrlSettings opsOutboundUrlSettings(
            @Value("${orbisops.channel.webhook.allow-loopback:false}") boolean allowLoopback,
            @Value("${orbisops.channel.webhook.allowed-hosts:}") String allowedHosts) {
        return OpsOutboundUrlSettings.fromRaw(allowLoopback, allowedHosts);
    }
}
