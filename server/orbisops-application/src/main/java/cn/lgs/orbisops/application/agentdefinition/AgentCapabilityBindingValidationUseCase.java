package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityReferenceSet;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilityBindingPolicy;

import java.util.ArrayList;
import java.util.List;

/** Coordinates project authorization checks for one Agent capability binding snapshot. */
public final class AgentCapabilityBindingValidationUseCase {

    private final AgentCapabilityAuthorizationPort authorizationPort;
    private final AgentCapabilityBindingPolicy bindingPolicy;

    public AgentCapabilityBindingValidationUseCase(
            AgentCapabilityAuthorizationPort authorizationPort,
            AgentCapabilityBindingPolicy bindingPolicy) {
        if (authorizationPort == null) {
            throw new IllegalArgumentException(
                    "AGENT_CAPABILITY_AUTHORIZATION_PORT_REQUIRED");
        }
        if (bindingPolicy == null) {
            throw new IllegalArgumentException(
                    "AGENT_CAPABILITY_BINDING_POLICY_REQUIRED");
        }
        this.authorizationPort = authorizationPort;
        this.bindingPolicy = bindingPolicy;
    }

    public AgentCapabilityBindingValidationResult validate(
            AgentCapabilityBindingSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "AGENT_CAPABILITY_BINDING_SNAPSHOT_REQUIRED");
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String projectId = snapshot.projectId();
        AgentCapabilityReferenceSet references = bindingPolicy.summarize(snapshot);
        if (projectId == null || projectId.isBlank()) {
            errors.add("Agent 必须归属一个项目");
            return new AgentCapabilityBindingValidationResult(
                    "", errors, warnings, references);
        }
        String normalizedProjectId = projectId.trim();
        if (!authorizationPort.projectExists(normalizedProjectId)) {
            errors.add("项目不存在：" + normalizedProjectId);
            return new AgentCapabilityBindingValidationResult(
                    normalizedProjectId, errors, warnings, references);
        }

        references.skillRefs().stream()
                .filter(skill -> !authorizationPort.skillAllowed(
                        normalizedProjectId, skill))
                .forEach(skill -> errors.add(
                        "Skill 未授权给项目 " + normalizedProjectId + "：" + skill));
        references.projectToolRefs().stream()
                .filter(tool -> !authorizationPort.projectToolAllowed(
                        normalizedProjectId, tool))
                .forEach(tool -> errors.add(
                        "MCP 工具未授权给项目 " + normalizedProjectId + "：" + tool));
        references.knowledgeBaseRefs().stream()
                .filter(knowledge -> !authorizationPort.knowledgeBaseAllowed(
                        normalizedProjectId, knowledge))
                .forEach(knowledge -> errors.add(
                        "知识库未授权给项目 " + normalizedProjectId + "：" + knowledge));
        references.executionTargetRefs().stream()
                .filter(target -> !authorizationPort.executionTargetEnabled(
                        normalizedProjectId, target))
                .forEach(target -> errors.add(
                        "执行目标未授权给项目 " + normalizedProjectId
                                + " 或已停用：" + target));
        if (!references.inlineMcpOwners().isEmpty()) {
            errors.add("不允许使用内联 MCP 配置："
                    + String.join("、", references.inlineMcpOwners())
                    + "。Agent 只能绑定当前项目已生成或已启用的 MCP 工具，"
                    + "确保统一授权、Tool Router 校验和审计。");
        }
        return new AgentCapabilityBindingValidationResult(
                normalizedProjectId,
                errors,
                warnings,
                references);
    }
}
