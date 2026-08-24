package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import com.alibaba.fastjson2.JSON;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Auxiliary calls share the normal runtime trace, with durable, payload-free correlation logs. */
public final class OpsAuxiliaryModelTrace {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(OpsAuxiliaryModelTrace.class);
    private static final List<String> FIELDS = List.of("requestedModel","responseModel","attempt","maxAttempts",
            "remainingMs","durationMs","elapsedMs","reasonCode","httpStatus","nextAttempt","delayMs","responseEmitted");
    private OpsAuxiliaryModelTrace() {}

    public static <T> T call(String kind,String input,String modelId,String apiId,String model,String path,Supplier<T> action) {
        var identity = identity(kind,input,modelId,apiId,model,path);
        var parent=OpsLlmTraceContext.current();
        Consumer<OpsRuntimeEvent> sink=event->{
            LOG.info("OPS_AUXILIARY_MODEL {}", CanonicalJson.stringifyPreservingOrder(safeRecord(identity,event)));
            if(parent!=null) parent.record(event);
        };
        var trace=new OpsLlmTraceContext.Trace(null,sink,kind,String.valueOf(identity.get("callId")),"MODEL",
                model,kind,parent==null?null:parent.skillFrame());
        long start=System.nanoTime();
        // Source Run IDs are correlation data, not a live foreground claim or cancellation authority.
        return cn.lgs.orbisops.trigger.ops.OpsRunControlContext.withBinding(null, () -> OpsLlmTraceContext.withTrace(trace,()->{
            record("AUXILIARY_MODEL_CALL_STARTED","RUNNING",Map.of("requestedModel",model));
            try {
                T result=action.get();
                record("AUXILIARY_MODEL_CALL_COMPLETED","SUCCEEDED",Map.of("durationMs",elapsed(start)));
                return result;
            } catch(RuntimeException failure) {
                record("AUXILIARY_MODEL_CALL_COMPLETED","FAILED",Map.of("durationMs",elapsed(start),"reasonCode",
                        OpsModelProviderFailureClassifier.classify(failure).map(OpsModelProviderFailureClassifier.Failure::code).orElse("MODEL_CALL_FAILED")));
                throw failure;
            }
        }));
    }

    public static void record(String type,String status,Map<String,Object> payload) {
        var trace=OpsLlmTraceContext.current();
        if(trace!=null)trace.record(OpsRuntimeEvent.builder().eventType(type).status(status).payload(payload).build());
    }

    static Map<String,Object> identity(String kind,String input,String modelId,String apiId,String model,String path) {
        var context=new LinkedHashMap<String,Object>();
        context.put("callId","aux-model-"+UUID.randomUUID());
        context.put("sourceKind",safeId(kind));context.put("modelId",safeId(modelId));context.put("apiId",safeId(apiId));
        context.put("requestedModel",safeId(model));
        context.put("completionsPath",path!=null && path.matches("/[A-Za-z0-9_./-]{1,200}")?path:"INVALID_PATH");
        try {
            context.put("inputHash",java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))));
            var root=JSON.parseObject(input);
            Map<?,?> task=root.get("taskContext") instanceof Map<?,?> provided?provided:root;
            for(String key:List.of("projectId","episodeId")) {
                Object value=task.get(key);
                if(value instanceof String text && !safeId(text).isEmpty()) context.put(key,text);
            }
            if(task.get("revision") instanceof Number revision && revision.longValue()>=0)context.put("episodeRevision",revision.longValue());
        } catch(RuntimeException invalidInput) { /* A legacy non-JSON prompt still receives its immutable input hash. */ }
          catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return Map.copyOf(context);
    }

    static Map<String,Object> safeRecord(Map<String,Object> identity,OpsRuntimeEvent event) {
        var record=new LinkedHashMap<>(identity);record.put("eventType",event.getEventType());record.put("status",event.getStatus());
        if(event.getPayload()!=null) for(String field:FIELDS) {
            Object value=event.getPayload().get(field);
            if(value instanceof Number || value instanceof Boolean)record.put(field,value);
            else if(value instanceof String text && !safeId(text).isEmpty())record.put(field,text);
        }
        return record;
    }
    private static String safeId(String value) { return value!=null && value.matches("[A-Za-z0-9_.-]{1,160}")?value:""; }
    private static long elapsed(long start){return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
}
