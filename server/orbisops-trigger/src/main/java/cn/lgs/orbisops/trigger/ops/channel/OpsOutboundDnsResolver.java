package cn.lgs.orbisops.trigger.ops.channel;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** External DNS boundary used by outbound SSRF validation. */
public interface OpsOutboundDnsResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
