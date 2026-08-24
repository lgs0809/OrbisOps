package cn.lgs.orbisops.trigger.application.episode;

import cn.lgs.orbisops.application.episode.TaskEpisodeStore;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpsTaskEpisodeContextCaptureTest {
    @Test void captureIncludesOriginalContextButNeverCopiesArbitraryAuthenticationMetadata() {
        var store=mock(TaskEpisodeStore.class);var capture=new OpsTaskEpisodeContextCapture(store);
        var metadata=new LinkedHashMap<String,Object>(Map.of("_trustedAuthPrincipal","credential-like fixture", "query","untrusted override"));
        var request=OpsAgentChatRequest.builder().projectId("p").sessionId("s").runId("r").query("original user goal").metadata(metadata).build();
        capture.capture(request,"complete original context",Map.of("contextBundleId","bundle","contextBundleHash","hash"));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> value=ArgumentCaptor.forClass(Map.class);
        verify(store).captureContext(eq("p"),eq("s"),eq("r"),value.capture());
        assertEquals(Set.of("originalUserQuery","memoryContext","contextBundleId","contextBundleHash"),value.getValue().keySet());
        assertEquals("complete original context",value.getValue().get("memoryContext"));
        assertFalse(value.getValue().toString().contains("credential-like"));assertEquals(metadata,request.getMetadata());
    }
    @Test void unavailableSnapshotStorageDoesNotFailOrRewriteMainWork() {
        var store=mock(TaskEpisodeStore.class);doThrow(new IllegalStateException("private fixture details")).when(store).captureContext(any(),any(),any(),any());
        var request=OpsAgentChatRequest.builder().projectId("p").sessionId("s").runId("r").query("unchanged").build();
        assertDoesNotThrow(()->new OpsTaskEpisodeContextCapture(store).capture(request,"original",Map.of()));
        assertEquals("unchanged",request.getQuery());
    }
}
