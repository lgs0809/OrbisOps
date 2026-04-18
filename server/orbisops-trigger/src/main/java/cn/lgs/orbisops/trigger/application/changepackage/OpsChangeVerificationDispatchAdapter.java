package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.*;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class OpsChangeVerificationDispatchAdapter implements ChangeVerificationDispatchPort {
    private final OpsAgentDefinitionQueryGateway definitions;
    private final ObjectProvider<OpsChatApplicationService> chats;
    private final IChangePackageCurrentRepository packages;
    private final AuthorizeProjectAccessUseCase access;
    private final cn.lgs.orbisops.application.security.AdminUserCatalogPort accounts;
    public OpsChangeVerificationDispatchAdapter(OpsAgentDefinitionQueryGateway definitions,
            ObjectProvider<OpsChatApplicationService> chats, IChangePackageCurrentRepository packages,
            AuthorizeProjectAccessUseCase access, cn.lgs.orbisops.application.security.AdminUserCatalogPort accounts) {
        this.definitions=definitions; this.chats=chats; this.packages=packages; this.access=access; this.accounts=accounts;
    }
    @Override public ChangeVerificationQueuePort.Binding binding(String projectId,String workflowId) {
        var definition = definitions.resolveForProject(workflowId,null,false,projectId);
        validate(definition,projectId);
        return new ChangeVerificationQueuePort.Binding(projectId,workflowId,definition.getVersion(),definition.getDefinitionHash());
    }
    @Override public String advance(ChangeVerificationQueuePort.Task task) {
        var current=packages.find(task.packageId()).orElseThrow(()->new SecurityException("CHANGE_VERIFICATION_PACKAGE_MISSING"));
        if (!task.projectId().equals(current.projectId()) || !task.owner().equals(current.createBy())
                || current.status()!=ChangePackageStatus.LANDED || !task.landingRunId().equals(current.landingRunId())
                || task.approvedVersion()!=current.pointer().approvedVersion()
                || !task.approvedHash().equals(current.pointer().approvedPackageHash()))
            throw new SecurityException("CHANGE_VERIFICATION_SOURCE_CHANGED");
        // Ownership is inherited, never promoted to the approver, worker or platform admin.
        var owner=accounts.findByUserId(task.owner());
        if(owner==null) owner=accounts.findByUsername(task.owner());
        if(owner==null || !Integer.valueOf(1).equals(owner.status())
                || !(task.owner().equals(owner.userId()) || task.owner().equals(owner.username()))) {
            throw new SecurityException("CHANGE_VERIFICATION_OWNER_UNAVAILABLE");
        }
        access.requireAccess(task.projectId(),owner.username(),owner.userId(),
                owner.role()==cn.lgs.orbisops.domain.security.AdminUserRole.ADMIN);
        var definition=definitions.resolveForProject(task.workflowId(),task.workflowVersion(),false,task.projectId());
        validate(definition,task.projectId());
        if (!task.workflowHash().equals(definition.getDefinitionHash()))
            throw new SecurityException("CHANGE_VERIFICATION_DEFINITION_CHANGED");
        var chat=chats.getObject();
        var existing=existing(chat,task);
        if (!existing.isEmpty()) return track(chat,task,existing);
        Map<String,Object> metadata=new LinkedHashMap<>();
        metadata.put("agentBindingMode","PINNED_VERSION");
        metadata.put("executionType","WORKFLOW");
        metadata.put("triggerSource","LANDING_COMPLETED");
        metadata.put("selectedChangePackageId",task.packageId());
        metadata.put("sourceLandingRunId",task.landingRunId());
        metadata.put("changeVerificationEventKey",task.eventKey());
        var request=OpsAgentChatRequest.builder().runId(task.runId()).sessionId(task.sessionId())
                .userId(task.owner()).projectId(task.projectId()).agentDefinitionId(task.workflowId())
                .agentVersion(task.workflowVersion()).agentDefinition(definition).mode("WORKFLOW").engine("GRAPH")
                .trustedObserveOnly(true).changeRequested(false).metadata(metadata)
                .query("变更已经完成。请按原批准标准只读验收已选中的方案，检查实际版本、完整观察窗口及业务指标，缺少证据时如实说明。")
                .build();
        chat.chat(request,task.owner());
        return track(chat,task,existing(chat,task));
    }
    private String track(OpsChatApplicationService chat,ChangeVerificationQueuePort.Task task,Map<String,Object> run) {
        if (run.isEmpty()) throw new IllegalStateException("CHANGE_VERIFICATION_RUN_NOT_COMMITTED");
        if (!task.workflowId().equals(run.get("agent_id")) || !task.workflowHash().equals(run.get("agent_definition_hash"))
                || !String.valueOf(task.workflowVersion()).equals(String.valueOf(run.get("agent_version")))
                || !task.owner().equals(run.get("user_id")) || !task.sessionId().equals(run.get("session_id")))
            throw new SecurityException("CHANGE_VERIFICATION_RUN_IDENTITY_CONFLICT");
        return switch(String.valueOf(run.get("status"))) {
            case "SUCCEEDED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            case "CANCELED" -> "CANCELED";
            case "RECOVERABLE" -> {chat.resumeRun(task.runId(),task.projectId(),task.owner()); yield "PENDING";}
            default -> "PENDING";
        };
    }
    private Map<String,Object> existing(OpsChatApplicationService chat,ChangeVerificationQueuePort.Task task) {
        try {return chat.run(task.runId(),task.projectId());}
        catch (IllegalArgumentException error) {
            if(error.getMessage()!=null && error.getMessage().startsWith("Work Session 不存在或不属于当前项目：")) return Map.of();
            throw error;
        }
    }
    private void validate(OpsAgentDefinition definition,String projectId) {
        if (definition==null || !"SPECIALIZED_WORKFLOW".equals(definition.getDefinitionKind())
                || !"PUBLISHED".equals(definition.getLifecycle()) || !projectId.equals(definition.getProjectId())
                || definition.getVersion()==null || definition.getVersion()<1 || definition.getDefinitionHash()==null
                || definition.getDefinitionHash().isBlank() || definition.getNodes()==null
                || !operation(definition,"SELECT_CHANGE_REQUEST") || !operation(definition,"CONTEXT_CHANGE"))
            throw new IllegalArgumentException("PUBLISHED_PROJECT_CHANGE_VERIFICATION_WORKFLOW_REQUIRED");
    }
    private boolean operation(OpsAgentDefinition definition,String operation) {
        return definition.getNodes().stream().anyMatch(n->n.getConfig()!=null
                && n.getConfig().get("changeVerification") instanceof Map<?,?> policy
                && operation.equals(policy.get("operation")));
    }
}
