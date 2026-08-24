package cn.lgs.orbisops.trigger.application.changepackage;
import cn.lgs.orbisops.application.changepackage.ChangeVerificationQueuePort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.model.*;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OpsChangeVerificationDispatchTest {
    final OpsAgentDefinitionQueryGateway definitions=mock(OpsAgentDefinitionQueryGateway.class);
    final OpsChatApplicationService chat=mock(OpsChatApplicationService.class);
    final IChangePackageCurrentRepository packages=mock(IChangePackageCurrentRepository.class);
    final cn.lgs.orbisops.application.security.AdminUserCatalogPort accounts=mock(cn.lgs.orbisops.application.security.AdminUserCatalogPort.class);
    final AuthorizeProjectAccessUseCase access=mock(AuthorizeProjectAccessUseCase.class);
    final ChangeVerificationQueuePort.Task task=new ChangeVerificationQueuePort.Task("event","p","cp",3,"hash","landing",
            "creator","wf",5,"def","run","session","lease",0);
    OpsChangeVerificationDispatchAdapter adapter;
    OpsAgentDefinition definition;
    ChangePackageCurrent current;
    @BeforeEach void setup() {
        ObjectProvider<OpsChatApplicationService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(chat);
        adapter=new OpsChangeVerificationDispatchAdapter(definitions,provider,packages,access,accounts);
        when(accounts.findByUserId("creator")).thenReturn(new cn.lgs.orbisops.domain.security.AdminUserAccount(
                1L,"creator","creator","",cn.lgs.orbisops.domain.security.AdminUserRole.USER,1,null,null));
        definition=OpsAgentDefinition.builder().agentId("wf").projectId("p").version(5).definitionHash("def")
                .lifecycle("PUBLISHED").definitionKind("SPECIALIZED_WORKFLOW").nodes(List.of(
                        OpsWorkflowNode.builder().nodeId("select").type("ROUTER").config(Map.of("changeVerification",Map.of("operation","SELECT_CHANGE_REQUEST"))).build(),
                        OpsWorkflowNode.builder().nodeId("context").type("ROUTER").config(Map.of("changeVerification",Map.of("operation","CONTEXT_CHANGE"))).build())).build();
        when(definitions.resolveForProject("wf",5,false,"p")).thenReturn(definition);
        current=mock(ChangePackageCurrent.class);when(packages.find("cp")).thenReturn(Optional.of(current));
        when(current.projectId()).thenReturn("p");when(current.createBy()).thenReturn("creator");
        when(current.status()).thenReturn(ChangePackageStatus.LANDED);when(current.landingRunId()).thenReturn("landing");
        when(current.pointer()).thenReturn(new ChangePackagePointer("cp",ChangePackageStatus.LANDED,3,"hash",3,"hash"));
    }
    Map<String,Object> run(String status) {return Map.of("status",status,"agent_id","wf","agent_version",5,
            "agent_definition_hash","def","user_id","creator","session_id","session");}
    @Test void automaticRunInheritsOwnerFrozenVersionAndReadOnlyAuthority() {
        when(chat.run("run","p")).thenThrow(new IllegalArgumentException("Work Session 不存在或不属于当前项目：run"))
                .thenReturn(run("SUCCEEDED"));
        assertEquals("COMPLETED",adapter.advance(task));
        var request=ArgumentCaptor.forClass(OpsAgentChatRequest.class);verify(chat).chat(request.capture(),eq("creator"));
        assertEquals("creator",request.getValue().getUserId());assertTrue(request.getValue().getTrustedObserveOnly());
        assertEquals(5,request.getValue().getAgentVersion());assertEquals("cp",request.getValue().getMetadata().get("selectedChangePackageId"));
        verify(access).requireAccess("p","creator","creator",false);
    }
    @Test void usesCurrentAccountIdentityWithoutChangingPersistedOwner() {
        when(accounts.findByUserId("creator")).thenReturn(new cn.lgs.orbisops.domain.security.AdminUserAccount(
                1L,"creator","actual-admin","",cn.lgs.orbisops.domain.security.AdminUserRole.ADMIN,1,null,null));
        when(chat.run("run","p")).thenReturn(run("SUCCEEDED"));
        assertEquals("COMPLETED",adapter.advance(task));
        verify(access).requireAccess("p","actual-admin","creator",true);
        verify(chat,never()).chat(any(),any());
    }
    @Test void disabledAccountCannotDispatchEvenIfItUsedToBeAdmin() {
        when(accounts.findByUserId("creator")).thenReturn(new cn.lgs.orbisops.domain.security.AdminUserAccount(
                1L,"creator","actual-admin","",cn.lgs.orbisops.domain.security.AdminUserRole.ADMIN,0,null,null));
        assertThrows(SecurityException.class,()->adapter.advance(task));
        verifyNoInteractions(chat,access);
    }
    @Test void lostSubmitResponseReusesAlreadyCommittedRun() {
        when(chat.run("run","p")).thenReturn(run("SUCCEEDED"));assertEquals("COMPLETED",adapter.advance(task));
        verify(chat,never()).chat(any(),any());
    }
    @Test void ownerRevocationPreventsDispatchOrResume() {
        doThrow(new SecurityException("revoked")).when(access).requireAccess("p","creator","creator",false);
        assertThrows(SecurityException.class,()->adapter.advance(task));verifyNoInteractions(chat);
    }
    @Test void identityConflictCannotBeReused() {
        var conflict=new HashMap<>(run("SUCCEEDED"));conflict.put("user_id","worker");when(chat.run("run","p")).thenReturn(conflict);
        assertThrows(SecurityException.class,()->adapter.advance(task));verify(chat,never()).chat(any(),any());
    }
    @Test void recoverableRunUsesOriginalOwnerAndRun() {
        when(chat.run("run","p")).thenReturn(run("RECOVERABLE"));assertEquals("PENDING",adapter.advance(task));
        verify(chat).resumeRun("run","p","creator");verify(chat,never()).chat(any(),any());
    }
    @Test void failedExecutionIsNotCompletedOrResubmitted() {
        when(chat.run("run","p")).thenReturn(run("FAILED"));assertEquals("FAILED",adapter.advance(task));
        verify(chat,never()).chat(any(),any());
    }
    @Test void changedApprovedSourceAndCrossProjectDefinitionAreDenied() {
        when(current.landingRunId()).thenReturn("other");assertThrows(SecurityException.class,()->adapter.advance(task));
        when(current.landingRunId()).thenReturn("landing");definition.setProjectId("other");
        assertThrows(IllegalArgumentException.class,()->adapter.advance(task));verifyNoInteractions(chat);
    }
    @Test void clientCannotSupplyTrustedReadOnlyAuthority() throws Exception {
        var request=new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"trustedObserveOnly\":false}",OpsAgentChatRequest.class);
        assertNull(request.getTrustedObserveOnly());
    }
}
