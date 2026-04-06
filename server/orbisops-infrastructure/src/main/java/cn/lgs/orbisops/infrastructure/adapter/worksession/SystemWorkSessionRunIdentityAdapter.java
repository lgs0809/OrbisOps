package cn.lgs.orbisops.infrastructure.adapter.worksession;

import cn.lgs.orbisops.application.worksession.run.WorkSessionRunIdentityPort;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/** Infrastructure-owned process identity, lease token and system time for Work Session runs. */
@Component
public class SystemWorkSessionRunIdentityAdapter implements WorkSessionRunIdentityPort {

    private final String workerId = resolveWorkerId();

    @Override
    public String newAttemptId() {
        return "attempt-" + UUID.randomUUID();
    }

    @Override
    public String newLeaseToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String workerId() {
        return workerId;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":"
                    + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception error) {
            return "worker:" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
