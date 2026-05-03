package cn.lgs.orbisops.application.episode;

import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskAcceptanceNaturalLanguageTest {
    final TaskAcceptancePort port=mock(TaskAcceptancePort.class);
    final TaskAcceptanceDraftModelPort model=mock(TaskAcceptanceDraftModelPort.class);
    final TaskAcceptanceApplicationService service=new TaskAcceptanceApplicationService(port,model);
    final TaskAcceptanceApplicationService.NaturalRequest input=new TaskAcceptanceApplicationService.NaturalRequest(2,"请核验本次目标版本查询");
    Map<String,Object> evidence() {return Map.of("goal","查询版本","revision",2,"pendingTurns",0,"receipts",List.of(Map.of(
            "resultId","receipt-1","outputHash","a".repeat(64),"content",Map.of("version","v1","latency",2.5))));}
    void setup() {
        when(port.draftEvidence("p","e","u",false)).thenReturn(evidence());when(port.inspect("p","e","u",false)).thenReturn(evidence());
        when(model.propose(anyString())).thenReturn(draft("receipt-1","/version","v1"));
    }
    TaskAcceptanceDraftModelPort.Draft draft(String result,String path,Object value) {return new TaskAcceptanceDraftModelPort.Draft(
            "READY","核对实际版本查询结果","核对只读目标版本查询，不外推健康或发布成功。",List.of(new TaskAcceptanceDraftModelPort.Check("目标版本",result,path,"EQ",value)));}
    Map<String,Object> propose() {return service.draft("p","e",input,"u",false);}
    @Test void naturalDraftUsesStoredHashAndNeverCommitsAcceptance() {
        setup();var result=propose();var request=(TaskAcceptanceRequest)result.get("request");
        assertEquals("READY",result.get("status"));assertEquals("a".repeat(64),request.criteria().get(0).outputHash());
        assertEquals(2,request.revision());assertNotNull(request.requestId());verify(port,never()).verify(any(),any(),any(),any(),anyBoolean());
    }
    @Test void modelReceivesOnlyBusinessPathsAsCheckableButKeepsMetadataAsContext() {
        setup();var receipt=Map.of("resultId","receipt-1","outputHash","a".repeat(64),"content",Map.of(
                "scope",Map.of("projectId","p","service","a1"),"status","AVAILABLE","version","v1",
                "items",List.of(Map.of("a/b~c",true))));
        when(port.draftEvidence("p","e","u",false)).thenReturn(Map.of("goal","查询版本","revision",2,"pendingTurns",0,"receipts",List.of(receipt)));
        propose();var input=org.mockito.ArgumentCaptor.forClass(String.class);verify(model).propose(input.capture());
        var json=com.alibaba.fastjson.JSON.parseObject(input.getValue());
        var pointers=json.getJSONArray("checkableFields").getJSONObject(0).getJSONArray("allowedPointers").toJavaList(String.class);
        assertEquals(Set.of("/version","/items/0/a~1b~0c"),Set.copyOf(pointers));
        assertEquals("a1",json.getJSONObject("task").getJSONArray("receipts").getJSONObject(0).getJSONObject("content").getJSONObject("scope").getString("service"));
    }
    @Test void foreignReceiptAndUnavailableFieldCannotBecomeReviewableAssertions() {
        setup();when(model.propose(anyString())).thenReturn(draft("foreign","/version","v1"));assertThrows(SecurityException.class,this::propose);
        when(model.propose(anyString())).thenReturn(draft("receipt-1","/missing","v1"));assertThrows(IllegalStateException.class,this::propose);
    }
    @Test void largeRawScrapesDoNotCrowdBusinessMetricsOutOfModelInput() {
        setup();var raw=cn.lgs.orbisops.domain.skill.SyntheticAcceptanceMetrics.receipt("p",0);
        var evidence=Map.of("goal","只读巡检","revision",2,"pendingTurns",0,"receipts",List.of(Map.of(
                "resultId","receipt-1","outputHash","a".repeat(64),"content",raw)));
        when(port.draftEvidence("p","e","u",false)).thenReturn(evidence);
        when(model.propose(anyString())).thenReturn(draft("receipt-1","/observedWindowV1/reachability","REACHABLE"));
        assertEquals("READY",propose().get("status"));
        var input=org.mockito.ArgumentCaptor.forClass(String.class);verify(model).propose(input.capture());
        var json=com.alibaba.fastjson.JSON.parseObject(input.getValue());
        var pointers=json.getJSONArray("checkableFields").getJSONObject(0).getJSONArray("allowedPointers").toJavaList(String.class);
        assertTrue(pointers.containsAll(List.of("/observedWindowV1/reachability","/observedWindowV1/errorRate","/observedWindowV1/p95Seconds")));
        assertFalse(json.getJSONObject("task").getJSONArray("receipts").getJSONObject(0).getJSONObject("content").containsKey("series"));
        assertTrue(raw.containsKey("series")); // Retained receipt and its digest are never rewritten.
        verify(port,never()).verify(any(),any(),any(),any(),anyBoolean());
    }
    @Test void evidenceMetadataAndInvalidModelShapeAreRejected() {
        setup();
        when(port.draftEvidence("p","e","u",false)).thenReturn(Map.of("goal","查询版本","revision",2,"pendingTurns",0,
                "receipts",List.of(Map.of("resultId","receipt-1","outputHash","a".repeat(64),
                        "content",Map.of("status","AVAILABLE","scope",Map.of("projectId","p"),"version","v1")))));
        when(model.propose(anyString())).thenReturn(draft("receipt-1","/status","AVAILABLE"));assertThrows(IllegalArgumentException.class,this::propose);
        when(model.propose(anyString())).thenReturn(new TaskAcceptanceDraftModelPort.Draft("READY","explanation","review summary",List.of()));
        assertThrows(IllegalArgumentException.class,this::propose);
    }
    @Test void aGenericToolBusinessStatusIsCheckableWithoutPretendingItIsTransportMetadata() {
        setup();
        when(port.draftEvidence("p","e","u",false)).thenReturn(Map.of("goal","核对健康状态","revision",2,"pendingTurns",0,
                "receipts",List.of(Map.of("resultId","receipt-1","outputHash","a".repeat(64),"content",Map.of("status","HEALTHY")))));
        when(model.propose(anyString())).thenReturn(draft("receipt-1","/status","HEALTHY"));
        assertEquals("READY",propose().get("status"));
        var input=org.mockito.ArgumentCaptor.forClass(String.class);verify(model).propose(input.capture());
        assertTrue(com.alibaba.fastjson.JSON.parseObject(input.getValue()).getJSONArray("checkableFields").getJSONObject(0)
                .getJSONArray("allowedPointers").contains("/status"));
        verify(port,never()).verify(any(),any(),any(),any(),anyBoolean());
    }
    @Test void changedOrPendingTaskDoesNotCommitAStaleModelResult() {
        setup();when(port.inspect("p","e","u",false)).thenReturn(Map.of("revision",3,"pendingTurns",0));assertThrows(IllegalStateException.class,this::propose);
        when(port.draftEvidence("p","e","u",false)).thenReturn(Map.of("revision",2,"pendingTurns",1));clearInvocations(model);
        assertThrows(IllegalStateException.class,this::propose);verifyNoInteractions(model);
    }
    @Test void missingEvidenceAndProviderFailureNeverCreateAnAcceptance() {
        setup();when(model.propose(anyString())).thenReturn(new TaskAcceptanceDraftModelPort.Draft("INSUFFICIENT_EVIDENCE","缺少持续观测数据。","",List.of()));
        assertEquals("INSUFFICIENT_EVIDENCE",propose().get("status"));
        when(model.propose(anyString())).thenThrow(new IllegalStateException("MODEL_PROVIDER_UNAVAILABLE"));assertThrows(IllegalStateException.class,this::propose);
        verify(port,never()).verify(any(),any(),any(),any(),anyBoolean());
    }
    @Test void failingBusinessExpectationIsRetainedInsteadOfChangedToObservedValue() {
        setup();when(model.propose(anyString())).thenReturn(draft("receipt-1","/version","v2"));
        var request=(TaskAcceptanceRequest)propose().get("request");assertEquals("v2",request.criteria().get(0).expected());
    }
    @Test void unauthorizedOrOversizedEvidenceDoesNotReachTheProvider() {
        setup();when(port.draftEvidence("p","e","u",false)).thenThrow(new SecurityException("forbidden"));assertThrows(SecurityException.class,this::propose);
        doReturn(Map.of("goal","x".repeat(64001),"revision",2,"pendingTurns",0,"receipts",evidence().get("receipts")))
                .when(port).draftEvidence("p","e","u",false);
        assertThrows(IllegalStateException.class,this::propose);verifyNoInteractions(model);
    }
}
