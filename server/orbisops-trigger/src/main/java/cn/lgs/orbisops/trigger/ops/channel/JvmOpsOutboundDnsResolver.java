package cn.lgs.orbisops.trigger.ops.channel;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** JVM DNS adapter for outbound webhook validation. */
@Component
public class JvmOpsOutboundDnsResolver implements OpsOutboundDnsResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
