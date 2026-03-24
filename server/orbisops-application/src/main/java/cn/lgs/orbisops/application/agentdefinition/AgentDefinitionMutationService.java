package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;

import java.util.Optional;

/** Application service that owns Agent Definition mutation ordering and catalog commit rules. */
public final class AgentDefinitionMutationService<D> {

    private final AgentDefinitionMutationModelPort<D> modelPort;
    private final AgentDefinitionMutationStorePort<D> storePort;
    private final AgentDefinitionMemoryCatalog<D> memoryCatalog;

    public AgentDefinitionMutationService(AgentDefinitionMutationModelPort<D> modelPort,
                                          AgentDefinitionMutationStorePort<D> storePort,
                                          AgentDefinitionMemoryCatalog<D> memoryCatalog) {
        if (modelPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MUTATION_MODEL_PORT_REQUIRED");
        }
        if (storePort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MUTATION_STORE_PORT_REQUIRED");
        }
        if (memoryCatalog == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MEMORY_CATALOG_REQUIRED");
        }
        this.modelPort = modelPort;
        this.storePort = storePort;
        this.memoryCatalog = memoryCatalog;
    }

    public D savePublished(D definition, boolean persistentStoreRequired) {
        if (definition == null) {
            throw new IllegalArgumentException("Agent 定义不能为空");
        }
        modelPort.assignVersion(definition, nextVersion(modelPort.agentId(definition)));
        if (!modelPort.hasLifecycle(definition)) {
            modelPort.assignLifecycle(definition, AgentDefinitionLifecycle.PUBLISHED);
        }
        prepare(definition, true, true);
        saveVersion(definition, true, persistentStoreRequired);
        memoryCatalog.register(definition, true);
        return modelPort.snapshot(definition);
    }

    public D saveDraft(D definition, boolean persistentStoreRequired) {
        if (definition == null || !hasText(modelPort.agentId(definition))) {
            throw new IllegalArgumentException("Agent 草稿缺少 agentId");
        }
        modelPort.assignVersion(definition, nextVersion(modelPort.agentId(definition)));
        modelPort.assignLifecycle(definition, AgentDefinitionLifecycle.DRAFT);
        prepare(definition, false, true);
        saveVersion(definition, false, persistentStoreRequired);
        memoryCatalog.register(definition, false);
        return modelPort.snapshot(definition);
    }

    public D validateVersion(String agentId,
                             Integer version,
                             boolean persistentStoreRequired) {
        D definition = requiredVersion(agentId, version);
        modelPort.assignLifecycle(definition, AgentDefinitionLifecycle.VALIDATED);
        prepare(definition, true, true);
        saveVersion(definition, false, persistentStoreRequired);
        memoryCatalog.register(definition, false);
        return modelPort.snapshot(definition);
    }

    public D publishVersion(String agentId,
                            Integer version,
                            boolean persistentStoreRequired) {
        D definition = requiredVersion(agentId, version);
        String expectedVersionHash = modelPort.definitionHash(definition);
        modelPort.assignLifecycle(definition, AgentDefinitionLifecycle.PUBLISHED);
        prepare(definition, true, false);
        requireStore(persistentStoreRequired);
        if (storePort.available()) {
            AgentDefinitionPublishResult result = storePort.publish(definition, expectedVersionHash);
            if (result != AgentDefinitionPublishResult.PUBLISHED
                    && result != AgentDefinitionPublishResult.ALREADY_CURRENT) {
                throw new IllegalStateException("AGENT_DEFINITION_PUBLISH_RESULT_INVALID");
            }
        }
        memoryCatalog.register(definition, true);
        return modelPort.snapshot(definition);
    }

    public boolean disableVersion(String agentId,
                                  Integer version,
                                  boolean persistentStoreRequired) {
        if (!hasText(agentId) || version == null || version <= 0) {
            return false;
        }
        requireStore(persistentStoreRequired);
        if (storePort.available() && !storePort.disableVersion(agentId.trim(), version)) {
            return false;
        }
        D disabled = memoryCatalog.version(agentId.trim(), version);
        if (disabled != null) {
            modelPort.assignLifecycle(disabled, AgentDefinitionLifecycle.DISABLED);
            memoryCatalog.register(disabled, false);
        }
        if (version.equals(memoryCatalog.currentVersion(agentId.trim()))) {
            memoryCatalog.removeCurrent(agentId.trim());
        }
        return true;
    }

    public boolean deleteCurrent(String agentId,
                                 String protectedAgentId,
                                 boolean persistentStoreRequired) {
        if (!hasText(agentId) || agentId.trim().equals(protectedAgentId)) {
            return false;
        }
        String normalizedAgentId = agentId.trim();
        requireStore(persistentStoreRequired);
        if (storePort.available()) {
            storePort.disableCurrent(normalizedAgentId);
        }
        memoryCatalog.removeCurrent(normalizedAgentId);
        return true;
    }

    public void registerLoaded(D definition, boolean current) {
        if (definition == null || !hasText(modelPort.agentId(definition))) {
            return;
        }
        Integer version = modelPort.version(definition);
        if (version == null || version <= 0) {
            modelPort.assignVersion(definition, 1);
        }
        if (!modelPort.hasLifecycle(definition)) {
            modelPort.assignLifecycle(definition, AgentDefinitionLifecycle.PUBLISHED);
        }
        modelPort.normalize(definition);
        modelPort.assignDefinitionHash(definition, modelPort.calculateHash(definition));
        memoryCatalog.register(definition, current);
    }

    /** Loading a published database snapshot must not silently create a new in-memory version. */
    public void registerStoredSnapshot(D definition, boolean current) {
        if (definition == null || !hasText(modelPort.agentId(definition))) return;
        if (hasText(modelPort.definitionHash(definition))
                && modelPort.version(definition) != null && modelPort.version(definition) > 0
                && modelPort.hasLifecycle(definition)) {
            memoryCatalog.register(definition, current);
            return;
        }
        // Legacy snapshots without a persisted identity retain the compatibility load path.
        registerLoaded(definition, current);
    }

    public Optional<D> findVersion(String agentId, Integer version) {
        if (!hasText(agentId) || version == null || version <= 0) {
            return Optional.empty();
        }
        String normalizedAgentId = agentId.trim();
        D inMemory = memoryCatalog.version(normalizedAgentId, version);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        return storePort.available()
                ? storePort.findVersion(normalizedAgentId, version)
                : Optional.empty();
    }

    private D requiredVersion(String agentId, Integer version) {
        return findVersion(agentId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent 版本不存在：" + agentId + "@" + version));
    }

    private int nextVersion(String agentId) {
        if (!hasText(agentId)) {
            return 1;
        }
        String normalizedAgentId = agentId.trim();
        int maxInMemory = memoryCatalog.maxVersion(normalizedAgentId);
        int maxInStore = storePort.available()
                ? storePort.maxVersion(normalizedAgentId)
                : 0;
        return Math.max(maxInMemory, maxInStore) + 1;
    }

    private void prepare(D definition, boolean validate, boolean assignUiSource) {
        modelPort.normalize(definition);
        if (validate) {
            modelPort.validate(definition);
        }
        if (assignUiSource) {
            modelPort.assignSource(definition, "UI");
        }
        modelPort.assignDefinitionHash(definition, modelPort.calculateHash(definition));
    }

    private void saveVersion(D definition,
                             boolean currentPublished,
                             boolean persistentStoreRequired) {
        requireStore(persistentStoreRequired);
        if (storePort.available()) {
            storePort.saveVersion(definition, currentPublished);
        }
    }

    private void requireStore(boolean persistentStoreRequired) {
        if (persistentStoreRequired && !storePort.available()) {
            throw new IllegalStateException("AGENT_DEFINITION_STORE_UNAVAILABLE");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
