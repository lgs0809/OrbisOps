package cn.lgs.orbisops.application.agent;

import java.util.Set;

public interface OpsMainAgentActionHandler {

    Set<OpsMainAgentActionType> supportedActions();

    OpsMainAgentOutcome handle(OpsMainAgentCommand command);
}
