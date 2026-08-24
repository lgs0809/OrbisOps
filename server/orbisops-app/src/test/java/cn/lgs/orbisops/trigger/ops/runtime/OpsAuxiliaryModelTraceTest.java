package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpsAuxiliaryModelTraceTest {
    @Test void sourceRunCorrelationNeverInheritsForegroundClaimOrCancellation() {
        var registry = new cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry();
        var control = new cn.lgs.orbisops.trigger.ops.OpsRunControlContext.Binding("run", "project", Map.of(), registry);
        registry.markCanceled("run");
        try (var scope = cn.lgs.orbisops.trigger.ops.OpsRunControlContext.open(control)) {
            assertEquals("independent", OpsAuxiliaryModelTrace.call("EPISODE_SEGMENT", "{\"sourceRunId\":\"run\"}",
                    "m", "a", "gpt-6-luna", "/v1/chat/completions", () -> {
                        assertNull(cn.lgs.orbisops.trigger.ops.OpsRunControlContext.current()); return "independent";
                    }));
            assertSame(control, cn.lgs.orbisops.trigger.ops.OpsRunControlContext.current());
        }
    }
    @Test void correlationIncludesRevisionAndHashWithoutPromptKeyOrProviderBody() {
        String input="{\"taskContext\":{\"projectId\":\"p\",\"episodeId\":\"e\",\"revision\":2},\"secret\":\"sk-test-private\",\"prompt\":\"raw untrusted input\"}";
        var identity=OpsAuxiliaryModelTrace.identity("TASK_ACCEPTANCE_DRAFT",input,"model-1","api-1","gpt-6-luna","/v1/chat/completions");
        assertEquals("p",identity.get("projectId"));assertEquals("e",identity.get("episodeId"));assertEquals(2L,identity.get("episodeRevision"));
        assertEquals(64,identity.get("inputHash").toString().length());
        var event=OpsRuntimeEvent.builder().eventType("MODEL_REQUEST_FAILED").status("FAILED").summary("provider secret")
                .payload(Map.of("httpStatus",503,"attempt",2,"durationMs",31L,"requestedModel","gpt-6-luna","apiKey","sk-other","rawBody","private")).build();
        var safe=OpsAuxiliaryModelTrace.safeRecord(identity,event);
        assertEquals(503,safe.get("httpStatus"));assertEquals("gpt-6-luna",safe.get("requestedModel"));
        String output=safe.toString();assertFalse(output.contains("sk-test"));assertFalse(output.contains("raw untrusted"));
        assertFalse(output.contains("apiKey"));assertFalse(output.contains("rawBody"));assertFalse(output.contains("provider secret"));
    }
    @Test void auxiliaryEventsJoinExistingTraceAndRestoreItOnFailure() {
        var events=new ArrayList<OpsRuntimeEvent>();
        var parent=new OpsLlmTraceContext.Trace(events,null,"owner","node","MODEL","luna","test");
        OpsLlmTraceContext.withTrace(parent,()->{
            assertThrows(IllegalStateException.class,()->OpsAuxiliaryModelTrace.call("TASK_ACCEPTANCE_DRAFT","{}","m","a","gpt-6-luna","/v1/chat/completions",()->{
                OpsAuxiliaryModelTrace.record("MODEL_REQUEST_ATTEMPT","RUNNING",Map.of("attempt",1));throw new IllegalStateException("MODEL_PROVIDER_UNAVAILABLE");
            }));
            assertSame(parent,OpsLlmTraceContext.current());return null;
        });
        assertNull(OpsLlmTraceContext.current());assertEquals(3,events.size());
        assertEquals("FAILED",events.get(2).getStatus());
    }
}
