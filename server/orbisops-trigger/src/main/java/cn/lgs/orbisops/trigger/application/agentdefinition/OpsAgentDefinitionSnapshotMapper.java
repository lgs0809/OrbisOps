package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsJsonSnapshotCodec;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeHashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Trigger compatibility mapper for persisted Agent Definition snapshots. */
@Slf4j
@Component
public class OpsAgentDefinitionSnapshotMapper
        implements AgentDefinitionSnapshotMapper<OpsAgentDefinition> {

    private final ObjectMapper objectMapper;

    public OpsAgentDefinitionSnapshotMapper() {
        this(new ObjectMapper());
    }

    OpsAgentDefinitionSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OpsAgentDefinition> fromSnapshot(AgentDefinitionSnapshot snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        try {
            OpsAgentDefinition definition = OpsJsonSnapshotCodec.read(
                    snapshot.definitionJson(), OpsAgentDefinition.class);
            definition.setVersion(snapshot.version());
            definition.setDefinitionHash(snapshot.definitionHash());
            definition.setLifecycle(snapshot.lifecycle().name());
            definition.setProjectId(snapshot.projectId());
            definition.setSource(snapshot.source());
            return Optional.of(definition);
        } catch (Exception error) {
            log.warn("跳过无法解析的 Agent 快照，agentId={}，原因={}",
                    snapshot.agentId(), error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public AgentDefinitionSnapshot toSnapshot(OpsAgentDefinition definition,
                                              boolean currentPublished) {
        if (definition == null || definition.getVersion() == null || definition.getVersion() <= 0) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VERSION_INVALID");
        }
        return new AgentDefinitionSnapshot(
                definition.getAgentId(),
                definition.getVersion(),
                definition.getDefinitionHash(),
                AgentDefinitionLifecycle.require(definition.getLifecycle()),
                definition.getName(),
                definition.getProjectId(),
                definition.getEngine(),
                definition.getDescription(),
                definition.getInstruction(),
                definition.getStartNodeId(),
                OpsJsonSnapshotCodec.write(definition),
                true,
                currentPublished,
                definition.getSource());
    }

    @Override
    public OpsAgentDefinition copy(OpsAgentDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        }
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsBytes(definition), OpsAgentDefinition.class);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "复制 Agent 定义失败：" + definition.getAgentId(), error);
        }
    }

    @Override
    public String definitionHash(OpsAgentDefinition definition) {
        try {
            OpsAgentDefinition hashInput = copy(definition);
            hashInput.setDefinitionHash(null);
            hashInput.setLifecycle(null);
            hashInput.setSource(null);
            return OpsRuntimeHashing.canonicalHash(hashInput);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "计算 Agent definitionHash 失败：" + definition.getAgentId(), error);
        }
    }
}
