package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpsWorkSessionRecoveryAdapter implements WorkSessionRecoveryPort {

    private final OpsWorkSessionRunAdapter runService;

    public OpsWorkSessionRecoveryAdapter(OpsWorkSessionRunAdapter runService) {
        this.runService = runService;
    }

    @Override
    public List<RecoveryDecision> recoverExpiredLeases(int limit) {
        return runService.recoverExpiredLeases(limit);
    }
}
