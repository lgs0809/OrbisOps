package cn.lgs.orbisops.application.worksession.run;

import java.time.Instant;

public interface WorkSessionRunIdentityPort {

    String newAttemptId();

    String newLeaseToken();

    String workerId();

    Instant now();
}
