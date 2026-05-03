package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.episode.TaskEpisodeApplicationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Component
public class OpsTaskEpisodeJob {
    private static final Logger LOG = LoggerFactory.getLogger(OpsTaskEpisodeJob.class);
    private final TaskEpisodeApplicationService service;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<String> sweepReason = new AtomicReference<>("RECOVERY");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "episode-replay"); thread.setDaemon(true); return thread;
    });
    public OpsTaskEpisodeJob(TaskEpisodeApplicationService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${orbisops.episodes.replay-delay-ms:5000}", initialDelayString = "${orbisops.episodes.replay-delay-ms:10000}")
    public void replay() {
        if (!running.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try {
                String reason = sweepReason.get();
                if (reason != null && service.sweep("", reason) < 1000) sweepReason.compareAndSet(reason, null);
                service.replay(8);
            } catch (RuntimeException error) {
                LOG.warn("Task episode replay failed: {}", error.getClass().getSimpleName());
            } finally { running.set(false); }
        });
    }
    @Scheduled(fixedDelayString = "${orbisops.episodes.idle-scan-delay-ms:60000}")
    public void idle() { sweepReason.compareAndSet(null, "IDLE"); replay(); }
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Shanghai")
    public void midnight() { sweepReason.set("MIDNIGHT"); replay(); }
    @PreDestroy public void close() { worker.shutdownNow(); }
}
