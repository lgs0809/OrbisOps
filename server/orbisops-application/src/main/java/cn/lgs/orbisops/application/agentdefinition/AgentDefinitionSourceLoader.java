package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Application input boundary for loading externally supplied Agent Definitions. */
public interface AgentDefinitionSourceLoader<T> {

    List<T> load(String locations);
}
