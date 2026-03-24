package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCatalogLoadResult;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCatalogLoadService;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMutationService;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionQueryService;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionTransactionPort;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishConflict;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Stable Gateway implementation; object graph construction belongs to Spring configuration. */
@Slf4j
@Service
public class OpsAgentDefinitionApplicationFacade implements OpsAgentDefinitionGateway {

    public static final String DEFAULT_AGENT_ID = OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID;

    private final OpsAgentCapabilityBindingQueryAdapter capabilityBindingQueryAdapter;
    private final AgentDefinitionMutationService<OpsAgentDefinition> mutationService;
    private final AgentDefinitionCatalogLoadService<OpsAgentDefinition> catalogLoadService;
    private final OpsAgentDefinitionDescriptorAdapter descriptorAdapter;
    private final OpsAgentDefinitionCatalogAdapter catalogAdapter;
    private final OpsProjectAgentDirectoryAdapter projectDirectoryAdapter;
    private final AgentDefinitionQueryService<OpsAgentDefinition> definitionQueryService;
    private final OpsAgentDefinitionRuntimeSettings runtimeSettings;
    private final AgentDefinitionTransactionPort transactionPort;

    public OpsAgentDefinitionApplicationFacade(
            OpsAgentDefinitionApplicationAssembly assembly,
            OpsAgentDefinitionRuntimeSettings runtimeSettings,
            AgentDefinitionTransactionPort transactionPort) {
        if (assembly == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_APPLICATION_ASSEMBLY_REQUIRED");
        }
        if (runtimeSettings == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_RUNTIME_SETTINGS_REQUIRED");
        }
        if (transactionPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_TRANSACTION_PORT_REQUIRED");
        }
        this.capabilityBindingQueryAdapter = assembly.capabilityBindingQueryAdapter();
        this.mutationService = assembly.mutationService();
        this.catalogLoadService = assembly.catalogLoadService();
        this.descriptorAdapter = assembly.descriptorAdapter();
        this.catalogAdapter = assembly.catalogAdapter();
        this.projectDirectoryAdapter = assembly.projectDirectoryAdapter();
        this.definitionQueryService = assembly.definitionQueryService();
        this.runtimeSettings = runtimeSettings;
        this.transactionPort = transactionPort;
    }

    @PostConstruct
    public void load() {
        String requestedDefaultAgentId = runtimeSettings.effectiveDefaultAgentId();
        AgentDefinitionCatalogLoadResult result = catalogLoadService.load(
                runtimeSettings.locations(),
                requestedDefaultAgentId,
                DEFAULT_AGENT_ID);
        result.rejectedProjectPlatformAgentIds().forEach(agentId ->
                log.info("跳过 classpath 中的项目 Agent 预设，项目 Agent 必须通过 API/SQL 接入，agentId={}", agentId));
        result.skippedStoredYamlCurrentAgentIds().forEach(agentId ->
                log.info("跳过数据库中的 YAML Agent 快照，使用 classpath 最新定义，agentId={}", agentId));
        if (!result.effectiveDefaultAgentId().equals(requestedDefaultAgentId)) {
            log.warn("默认运维 Agent 定义不存在，defaultAgentId={}，降级为 {}",
                    requestedDefaultAgentId, result.effectiveDefaultAgentId());
        }
        runtimeSettings.useEffectiveDefaultAgentId(result.effectiveDefaultAgentId());
        if (result.storedCurrentDefinitionCount() > 0) {
            log.info("从 MySQL 加载运维 Agent 定义，count={}", result.storedCurrentDefinitionCount());
        }
        log.info("运维 Agent 定义加载完成，count={}，default={}",
                result.currentDefinitionCount(), runtimeSettings.effectiveDefaultAgentId());
    }

    @Override
    public OpsAgentDefinition resolve(String agentDefinitionId) {
        return resolve(agentDefinitionId, null, false);
    }

    @Override
    public OpsAgentDefinition resolve(String agentDefinitionId,
                                      Integer agentVersion,
                                      boolean previewDraft) {
        return definitionQueryService.resolve(agentDefinitionId, agentVersion, previewDraft);
    }

    @Override
    public OpsAgentDefinition resolveForProject(String agentDefinitionId,
                                                Integer agentVersion,
                                                boolean previewDraft,
                                                String projectId) {
        return definitionQueryService.resolveForProject(
                agentDefinitionId, agentVersion, previewDraft, projectId);
    }

    @Override
    public List<OpsAgentDefinition> list() {
        return definitionQueryService.list();
    }

    @Override
    public List<OpsAgentDefinition> listForProject(String projectId) {
        return definitionQueryService.listForProject(projectId);
    }

    public String defaultAgentId() {
        return catalogAdapter.defaultAgentId();
    }

    public OpsAgentDefinition findCurrent(String agentId) {
        return catalogAdapter.findCurrent(agentId);
    }

    public OpsAgentDefinition findVersion(String agentId, int version) {
        return catalogAdapter.findVersion(agentId, version);
    }

    public List<OpsAgentDefinition> findCurrentDefinitions() {
        return catalogAdapter.findCurrentDefinitions();
    }

    public List<OpsAgentDefinition> findVersions(String agentId) {
        return catalogAdapter.findVersions(agentId);
    }

    @Override
    public AgentDefinitionVersionState describe(OpsAgentDefinition definition) {
        return descriptorAdapter.describe(definition);
    }

    public OpsAgentDefinition snapshot(OpsAgentDefinition definition) {
        return descriptorAdapter.snapshot(definition);
    }

    public boolean available() {
        return projectDirectoryAdapter.available();
    }

    public boolean exists(String projectId) {
        return projectDirectoryAdapter.exists(projectId);
    }

    public String defaultAgentId(String projectId) {
        return projectDirectoryAdapter.defaultAgentId(projectId);
    }

    public synchronized OpsAgentDefinition save(OpsAgentDefinition definition) {
        return transactionPort.required(() ->
                mutationService.savePublished(
                        definition,
                        runtimeSettings.jdbcEnabled()));
    }

    @Override
    public synchronized OpsAgentDefinition saveDraft(OpsAgentDefinition definition) {
        return transactionPort.required(() ->
                mutationService.saveDraft(
                        definition,
                        runtimeSettings.jdbcEnabled()));
    }

    @Override
    public synchronized OpsAgentDefinition validateVersion(
            String agentId,
            Integer version) {
        return transactionPort.required(() ->
                mutationService.validateVersion(
                        agentId,
                        version,
                        runtimeSettings.jdbcEnabled()));
    }

    @Override
    public synchronized OpsAgentDefinition publishVersion(
            String agentId,
            Integer version) {
        return transactionPort.required(() ->
                publishVersionInTransaction(agentId, version));
    }

    private OpsAgentDefinition publishVersionInTransaction(
            String agentId,
            Integer version) {
        try {
            return mutationService.publishVersion(
                    agentId,
                    version,
                    runtimeSettings.jdbcEnabled());
        } catch (AgentDefinitionPublishConflict conflict) {
            if (conflict.reason()
                    == AgentDefinitionPublishConflict.Reason.VERSION_CHANGED) {
                throw new IllegalStateException(
                        "AGENT_VERSION_PUBLISH_CAS_FAILED：候选版本已变化或未完成校验",
                        conflict);
            }
            throw new IllegalStateException(
                    "AGENT_PUBLISH_MVCC_CONFLICT：当前发布版本已变化",
                    conflict);
        }
    }

    @Override
    public synchronized OpsAgentDefinition rollback(
            String agentId,
            Integer version) {
        return transactionPort.required(() ->
                publishVersionInTransaction(agentId, version));
    }

    @Override
    public synchronized boolean disableVersion(
            String agentId,
            Integer version) {
        return transactionPort.required(() ->
                mutationService.disableVersion(
                        agentId,
                        version,
                        runtimeSettings.jdbcEnabled()));
    }

    @Override
    public List<OpsAgentDefinition> listVersions(String agentId) {
        return definitionQueryService.versions(agentId);
    }

    @Override
    public synchronized void reload() {
        load();
    }

    @Override
    public synchronized boolean delete(String agentId) {
        return transactionPort.required(() ->
                mutationService.deleteCurrent(
                        agentId,
                        DEFAULT_AGENT_ID,
                        runtimeSettings.jdbcEnabled()));
    }

    @Override
    public List<Map<String, Object>> listCapabilityBindings(String agentId) {
        return capabilityBindingQueryAdapter.list(agentId);
    }
}
