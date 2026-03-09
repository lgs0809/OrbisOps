package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import com.alibaba.fastjson.JSON;

/** Resolves, verifies, parses, and freezes the authoritative Agent definition snapshot. */
final class OpsAnalysisAgentDefinitionSnapshotResolver {

    private final OpsAgentDefinitionQueryGateway agentDefinitions;

    OpsAnalysisAgentDefinitionSnapshotResolver(OpsAgentDefinitionQueryGateway agentDefinitions) {
        this.agentDefinitions = agentDefinitions;
    }

    OpsAgentDefinition resolve(OpsAgentRunRequestDTO request, String projectId) {
        OpsAgentDefinition snapshot = parse(request.getAgentDefinitionSnapshotJson());
        if (snapshot != null) {
            validateSnapshot(snapshot, projectId);
            OpsAgentDefinition authoritative = agentDefinitions.resolveForProject(
                    snapshot.getAgentId(),
                    snapshot.getVersion(),
                    false,
                    projectId);
            if (!snapshot.getDefinitionHash().equals(authoritative.getDefinitionHash())) {
                throw new SecurityException("AGENT_DEFINITION_HASH_MISMATCH：请求快照不是后端已发布版本");
            }
            return authoritative;
        }
        return agentDefinitions.resolveForProject(
                request.getAgentDefinitionId(),
                request.getAgentVersion(),
                false,
                projectId);
    }

    OpsAgentDefinition parse(String snapshotJson) {
        if (!hasText(snapshotJson)) {
            return null;
        }
        try {
            return JSON.parseObject(snapshotJson, OpsAgentDefinition.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Agent 编排快照 JSON 不合法", e);
        }
    }

    String serialize(OpsAgentDefinition snapshot) {
        return snapshot == null ? null : JSON.toJSONString(snapshot);
    }

    private void validateSnapshot(OpsAgentDefinition snapshot, String projectId) {
        if (hasText(snapshot.getProjectId()) && !snapshot.getProjectId().trim().equals(projectId)) {
            throw new IllegalArgumentException("Agent 编排快照属于项目 " + snapshot.getProjectId()
                    + "，不能在项目 " + projectId + " 中运行");
        }
        if (!hasText(snapshot.getAgentId())
                || snapshot.getVersion() == null
                || snapshot.getVersion() <= 0
                || !hasText(snapshot.getDefinitionHash())) {
            throw new IllegalArgumentException("Agent 编排快照缺少 agentId/version/definitionHash");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
