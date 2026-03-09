package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;

public interface AlertRuleAgentResolverPort {
    AlertAgentResolution resolve(AlertRuleCandidate candidate);
}
