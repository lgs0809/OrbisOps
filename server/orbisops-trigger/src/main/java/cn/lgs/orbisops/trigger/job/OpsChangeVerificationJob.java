package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.changepackage.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class OpsChangeVerificationJob {
    private final ChangeVerificationApplicationService service;
    private final Map<String,String> bindings;
    private final boolean enabled;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"change-verification");thread.setDaemon(true);return thread;
    });
    public OpsChangeVerificationJob(ChangeVerificationQueuePort queue,ChangeVerificationDispatchPort dispatch,
            @Value("${orbisops.change-verification.enabled:false}") boolean enabled,
            @Value("${orbisops.change-verification.bindings:{}}") String bindings) throws java.io.IOException {
        this.service=new ChangeVerificationApplicationService(queue,dispatch);
        this.enabled=enabled;
        this.bindings=Map.copyOf(new ObjectMapper().readValue(bindings,new TypeReference<Map<String,String>>(){}));
    }
    @Scheduled(fixedDelayString="${orbisops.change-verification.poll-ms:10000}",initialDelay=30000)
    public void replay() {
        if(!enabled || !running.compareAndSet(false,true)) return;
        worker.execute(()->{
            try {service.replay(bindings);}
            catch(RuntimeException error) {LoggerFactory.getLogger(getClass()).warn("Change verification deferred: {}",error.getClass().getSimpleName());}
            finally {running.set(false);}
        });
    }
    @PreDestroy public void close() {worker.shutdownNow();}
}
