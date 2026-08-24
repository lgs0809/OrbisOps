package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Loopback inference fixtures verify lifecycle/deadline handling, not Qwen/model quality. */
class OpsSkillRouteProjectionWorkerTest {
    private static final String REVISION="a".repeat(40);
    private final SkillRouteProjectionSourcePort source=mock(SkillRouteProjectionSourcePort.class);
    private final SkillRouteIndexPort index=mock(SkillRouteIndexPort.class);
    private final SkillRoutePublicationPort publication=mock(SkillRoutePublicationPort.class);

    @Test void slowBackgroundEncodingCanFinishBeyondTheForegroundFiveSecondBudget() throws Exception {
        try(var gateway=new Gateway(6200,false)) {
            var model=gateway.client();var candidate=fixture();
            new OpsSkillRouteProjectionWorker(source,index,model,publication).projectPage();
            assertEquals(1,gateway.requests.get());
            verify(index).ready(eq(candidate),eq(model.modelIdentity()),argThat(vector->vector.length==1024 && vector[0]==1F));
            verify(publication).activate(candidate,model.modelIdentity());
        }
    }

    @Test void busyDependencyLeavesProjectionUnpublishedAndNextScanCanRecover() throws Exception {
        try(var gateway=new Gateway(0,true)) {
            var model=gateway.client();var candidate=fixture();
            var worker=new OpsSkillRouteProjectionWorker(source,index,model,publication);
            assertDoesNotThrow(worker::projectPage);
            verify(index,never()).ready(any(),anyString(),any());
            verify(publication,never()).activate(any(),anyString());
            worker.projectPage();
            assertEquals(2,gateway.requests.get());
            verify(index).ready(eq(candidate),eq(model.modelIdentity()),any());
            verify(publication).activate(candidate,model.modelIdentity());
        }
    }

    @Test void changedSourceAfterEncodingCannotMarkTheGenerationReady() throws Exception {
        try(var gateway=new Gateway(0,false)) {
            var model=gateway.client();fixture();when(source.current(any())).thenReturn(false);
            new OpsSkillRouteProjectionWorker(source,index,model,publication).projectPage();
            assertEquals(1,gateway.requests.get());
            verify(index,never()).ready(any(),anyString(),any());
        }
    }

    @Test void readyGenerationCanResumePointerActivationWithoutAnotherModelCall() {
        var model=mock(OpsSkillRetrievalHttpClient.class);when(model.configured()).thenReturn(true);
        when(model.modelIdentity()).thenReturn("fixture-ready-identity");
        var candidate=fixture();when(index.contains(candidate,model.modelIdentity())).thenReturn(true);
        new OpsSkillRouteProjectionWorker(source,index,model,publication).projectPage();
        verify(model,never()).embed(anyString(),anyBoolean());
        verify(model,never()).embedBackground(anyString(),anyBoolean());
        verify(index,never()).stage(any(),anyString());
        verify(publication).activate(candidate,model.modelIdentity());
    }

    @Test void scheduledTickReturnsWithoutWaitingAndDoesNotQueueDuplicateScans() throws Exception {
        var model=mock(OpsSkillRetrievalHttpClient.class);when(model.configured()).thenReturn(true);
        when(model.modelIdentity()).thenReturn("fixture-slow-identity");fixture();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var activated=new CountDownLatch(1);
        when(model.embedBackground(anyString(),eq(false))).thenAnswer(call->{
            entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("FIXTURE_RELEASE_TIMEOUT");return new float[1024];
        });
        when(publication.activate(any(),anyString())).thenAnswer(call->{activated.countDown();return true;});
        try(var worker=new OpsSkillRouteProjectionWorker(source,index,model,publication)) {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),worker::scan);
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int tick=0;tick<10;tick++)assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),worker::scan);
            verify(source,times(1)).page(anyLong(),eq(10));
            verify(publication,never()).activate(any(),anyString());
            release.countDown();assertTrue(activated.await(2,TimeUnit.SECONDS));
        } finally {release.countDown();}
    }

    @Test void shutdownInterruptsOwnedEncodingAndDoesNotPublishPartialWork() throws Exception {
        var model=mock(OpsSkillRetrievalHttpClient.class);when(model.configured()).thenReturn(true);
        when(model.modelIdentity()).thenReturn("fixture-interrupt-identity");fixture();
        var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);
        when(model.embedBackground(anyString(),eq(false))).thenAnswer(call->{
            entered.countDown();
            try {new CountDownLatch(1).await();throw new AssertionError("Expected owned shutdown interrupt");}
            catch(InterruptedException stop) {interrupted.countDown();Thread.currentThread().interrupt();throw new IllegalStateException("SKILL_RETRIEVAL_INTERRUPTED",stop);}
        });
        var worker=new OpsSkillRouteProjectionWorker(source,index,model,publication);
        try {
            worker.scan();assertTrue(entered.await(2,TimeUnit.SECONDS));
            worker.close();assertTrue(interrupted.await(2,TimeUnit.SECONDS));
            assertDoesNotThrow(worker::scan);
            verify(index,never()).ready(any(),anyString(),any());
            verify(publication,never()).activate(any(),anyString());
            verify(source,times(1)).page(anyLong(),eq(10));
        } finally {worker.close();}
    }

    private SkillRuntimeCandidate fixture() {
        var manifest=CanonicalJson.stringify(Map.of("routingProfile",Map.of("whenToUse",List.of("只读核对指定目录服务"),"whenNotToUse",List.of("不用于执行资源变更"))));
        var entry=new SkillCatalogEntry(1,"fixture-method","fixture-project","目录核对方法","PROJECT","","读取指定服务的当前回执","# 目录核对\n核对唯一记录及资源标识。",1,"ENABLED","fixture",null,null,"MANUAL","AUTO",true,true,null,"","",null,"b".repeat(64),1,"b".repeat(64),1,"c".repeat(64),manifest,"{}");
        var candidate=SkillCatalogSnapshot.fromView(new SkillCatalogViewMapper().toView(entry,false)).runtimeCandidate();
        assertTrue(candidate.activeAtUse());assertTrue(candidate.routingReady());
        when(source.page(anyLong(),eq(10))).thenReturn(List.of(entry));when(source.current(any())).thenReturn(true);
        return candidate;
    }

    private static final class Gateway implements AutoCloseable {
        final HttpServer server;final ExecutorService worker;final AtomicInteger requests=new AtomicInteger();
        Gateway(long delayMillis,boolean failFirst) throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"projection-fixture-http");t.setDaemon(true);return t;});
            server.setExecutor(worker);
            server.createContext("/embed",exchange->{
                int request=requests.incrementAndGet();exchange.getRequestBody().readAllBytes();
                try {
                    if(delayMillis>0)Thread.sleep(delayMillis);
                    if(failFirst && request==1) {exchange.sendResponseHeaders(503,-1);return;}
                    var vector=new ArrayList<Float>(Collections.nCopies(1024,0F));vector.set(0,1F);
                    byte[] response=CanonicalJson.stringify(Map.of("model",OpsSkillRetrievalHttpClient.EMBEDDING,"revision",REVISION,"preprocessing",OpsSkillRetrievalHttpClient.PREPROCESS,"embedding",vector)).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
                } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();}
                finally {exchange.close();}
            });server.start();
        }
        OpsSkillRetrievalHttpClient client() {return new OpsSkillRetrievalHttpClient("http://127.0.0.1:"+server.getAddress().getPort(),"",REVISION,REVISION);}
        @Override public void close() {server.stop(0);worker.shutdownNow();}
    }
}
