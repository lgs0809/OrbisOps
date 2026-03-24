package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory current/version catalog used by the Agent Definition compatibility boundary. */
public final class AgentDefinitionMemoryCatalog<D> {

    private final AgentDefinitionDescriptorPort<D> descriptorPort;
    private final Map<String, D> currentDefinitions = new LinkedHashMap<>();
    private final Map<String, NavigableMap<Integer, D>> versions = new LinkedHashMap<>();

    public AgentDefinitionMemoryCatalog(AgentDefinitionDescriptorPort<D> descriptorPort) {
        if (descriptorPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_DESCRIPTOR_PORT_REQUIRED");
        }
        this.descriptorPort = descriptorPort;
    }

    public synchronized void clear() {
        currentDefinitions.clear();
        versions.clear();
    }

    public synchronized void register(D definition, boolean current) {
        D stored = descriptorPort.snapshot(definition);
        AgentDefinitionVersionState state = descriptorPort.describe(stored);
        versions.computeIfAbsent(state.agentId(), ignored -> new TreeMap<>())
                .put(state.version(), stored);
        if (current) {
            currentDefinitions.put(state.agentId(), stored);
        }
    }

    public synchronized D current(String agentId) {
        D definition = currentDefinitions.get(agentId);
        return definition == null ? null : descriptorPort.snapshot(definition);
    }

    public synchronized List<D> currentDefinitions() {
        return currentDefinitions.values().stream()
                .map(descriptorPort::snapshot)
                .toList();
    }

    public synchronized int currentSize() {
        return currentDefinitions.size();
    }

    public synchronized D version(String agentId, int version) {
        D definition = Optional.ofNullable(versions.get(agentId))
                .map(items -> items.get(version))
                .orElse(null);
        return definition == null ? null : descriptorPort.snapshot(definition);
    }

    public synchronized List<D> versions(String agentId) {
        NavigableMap<Integer, D> agentVersions = versions.get(agentId);
        return agentVersions == null
                ? List.of()
                : agentVersions.descendingMap().values().stream()
                .map(descriptorPort::snapshot)
                .toList();
    }

    public synchronized boolean containsCurrent(String agentId) {
        return currentDefinitions.containsKey(agentId);
    }

    public synchronized boolean containsVersion(String agentId, int version) {
        return Optional.ofNullable(versions.get(agentId))
                .map(items -> items.containsKey(version))
                .orElse(false);
    }

    public synchronized int maxVersion(String agentId) {
        return Optional.ofNullable(versions.get(agentId))
                .filter(items -> !items.isEmpty())
                .map(NavigableMap::lastKey)
                .orElse(0);
    }

    public synchronized D removeCurrent(String agentId) {
        D removed = currentDefinitions.remove(agentId);
        return removed == null ? null : descriptorPort.snapshot(removed);
    }

    public synchronized Integer currentVersion(String agentId) {
        D definition = currentDefinitions.get(agentId);
        return definition == null ? null : descriptorPort.describe(definition).version();
    }
}
