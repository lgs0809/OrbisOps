package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.application.skill.SkillModelTransportDeferredException;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsBackgroundModelCallDeadline;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson.JSON;
import com.sun.net.httpserver.HttpServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact deployed adapters and real loopback HTTP; synthetic slow provider, no business/model-quality claim. */
public class BackgroundModelDeadlineProbe {
    public static void main(String[] args) throws Exception {
        var requests = new AtomicInteger(); var arrived = new CountDownLatch(1);
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var peerExecutor = Executors.newCachedThreadPool(); peer.setExecutor(peerExecutor);
        peer.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes(); requests.incrementAndGet(); arrived.countDown();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { Thread.sleep(65_000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        }); peer.start();
        var result = new LinkedHashMap<String,Object>();
        result.put("scope", "REAL_LOOPBACK_TRANSPORT_SYNTHETIC_SLOW_PROVIDER_NO_BUSINESS_WRITES");
        result.put("startedAt", java.time.Instant.now().toString());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("explicit-local-protocol-fixture",
                    Map.of("orbisops.ai.model-calls-enabled", true)));
            context.register(SpringAiModelAvailabilityAdapter.class); context.refresh();
            var model = new AiClientModelDefinition(null,"synthetic-deadline-terra","synthetic-local-peer",
                    OpsSkillAuthoringModelClient.MODEL,"CHAT","SKILL_AUTHORING","synthetic transport only",1,null,null);
            var api = new AiClientApiDefinition(null,model.apiId(),"synthetic slow peer","OPENAI_COMPATIBLE",
                    "http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only","/v1/chat/completions",null,1,null,null);
            var models = (AiClientModelCatalogPort)Proxy.newProxyInstance(BackgroundModelDeadlineProbe.class.getClassLoader(),
                    new Class<?>[]{AiClientModelCatalogPort.class},(p,m,a) -> {
                        if(m.getName().equals("listEnabled")) return List.of(model);
                        throw new UnsupportedOperationException("READ_ONLY_TRANSPORT_PROBE"); });
            var apis = (AiClientApiCatalogPort)Proxy.newProxyInstance(BackgroundModelDeadlineProbe.class.getClassLoader(),
                    new Class<?>[]{AiClientApiCatalogPort.class},(p,m,a) -> {
                        if(m.getName().equals("findByApiId")) return api;
                        throw new UnsupportedOperationException("READ_ONLY_TRANSPORT_PROBE"); });
            var client = new OpsSkillAuthoringModelClient(models,apis,context.getBean(SpringAiModelAvailabilityAdapter.class),
                    new OpsSecretResolver(context.getEnvironment()));
            var started = System.nanoTime();
            var modelTask = new FutureTask<Boolean>(() -> {
                try { client.generate("Synthetic total HTTP waiting deadline only", "{}"); return false; }
                catch (SkillModelTransportDeferredException deferred) { return true; }
            });
            var caller = new Thread(modelTask,"deadline-probe-caller"); caller.setDaemon(true); caller.start();
            if (!arrived.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("PROBE_HTTP_NOT_DISPATCHED");
            var independentStarted = System.nanoTime();
            var independent = OpsBackgroundModelCallDeadline.call(60,() -> "independent-call-available");
            result.put("independentCallMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-independentStarted));
            result.put("independentCallSucceeded",independent.equals("independent-call-available"));
            boolean deferred = modelTask.get(64,TimeUnit.SECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            result.put("elapsedMillis",elapsed); result.put("physicalRequests",requests.get());
            result.put("deferredForPersistentRetry",deferred); result.put("maximumSeconds",OpsBackgroundModelCallDeadline.MAX_SECONDS);
            long cleanupDeadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            long workers;
            do {
                workers=Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> thread.isAlive() && thread.getName().equals("ops-background-model")).count();
                if (workers==0) break;
                Thread.sleep(20);
            } while (System.nanoTime()<cleanupDeadline);
            result.put("remainingModelWorkers",workers);
            boolean pass = deferred && requests.get()==1 && elapsed>=59_000 && elapsed<63_000
                    && workers==0 && Boolean.TRUE.equals(result.get("independentCallSucceeded"))
                    && ((Long)result.get("independentCallMillis"))<1000;
            result.put("status",pass?"PASS":"FAIL");
            if (!pass) throw new IllegalStateException("PROBE_DEADLINE_INVARIANT_FAILED");
        } finally {
            peer.stop(0); peerExecutor.shutdownNow(); result.put("finishedAt",java.time.Instant.now().toString());
            Files.writeString(Path.of(args[0]),JSON.toJSONString(result,true),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }
    }
}
