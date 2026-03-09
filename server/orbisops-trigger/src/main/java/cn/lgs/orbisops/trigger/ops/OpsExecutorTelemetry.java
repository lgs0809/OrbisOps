package cn.lgs.orbisops.trigger.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/** Saturation gauges for the bounded platform executors. */
@Component
public final class OpsExecutorTelemetry {

    private static final OpsTraceAwareExecutorFactory METRICS =
            new OpsTraceAwareExecutorFactory();

    public OpsExecutorTelemetry(
            ObjectProvider<MeterRegistry> registryProvider,
            @Qualifier("opsSubAgentExecutor") ThreadPoolExecutor subAgent,
            @Qualifier("opsRunExecutor") ThreadPoolExecutor run,
            @Qualifier("opsModelCallExecutor") ThreadPoolExecutor model,
            @Qualifier("ragIngestionExecutor") ThreadPoolExecutor rag,
            @Qualifier("opsMemoryExecutor") ThreadPoolExecutor memory) {
        MeterRegistry registry = registryProvider == null
                ? null
                : registryProvider.getIfAvailable();
        if (registry == null) return;
        List.of(
                new Pool("sub_agent", subAgent),
                new Pool("run", run),
                new Pool("model_call", model),
                new Pool("rag_ingestion", rag),
                new Pool("memory", memory))
                .forEach(pool -> bind(registry, pool));
    }

    private void bind(MeterRegistry registry, Pool pool) {
        Gauge.builder("ops_executor_active_threads", pool.executor(),
                        ThreadPoolExecutor::getActiveCount)
                .tag("executor", pool.name())
                .description("Active worker threads in a bounded operations executor")
                .register(registry);
        Gauge.builder("ops_executor_queue_depth", pool.executor(),
                        executor -> executor.getQueue().size())
                .tag("executor", pool.name())
                .description("Queued tasks in a bounded operations executor")
                .register(registry);
        Gauge.builder("ops_executor_queue_remaining", pool.executor(),
                        executor -> executor.getQueue().remainingCapacity())
                .tag("executor", pool.name())
                .description("Remaining queue capacity in a bounded operations executor")
                .register(registry);
        Gauge.builder("ops_executor_rejections_total", pool.executor(),
                        METRICS::rejectedCount)
                .tag("executor", pool.name())
                .description("Tasks handed to the executor rejection policy")
                .register(registry);
    }

    private record Pool(String name, ThreadPoolExecutor executor) {
        private Pool {
            if (executor == null) {
                throw new IllegalArgumentException("OPS_EXECUTOR_TELEMETRY_POOL_REQUIRED:" + name);
            }
        }
    }
}
