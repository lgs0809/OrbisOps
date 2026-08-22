package cn.lgs.orbisops.trigger.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ThreadPoolExecutor;

/** Composition root for bounded trace-aware operations executors. */
@Configuration
public class OpsMultiAgentExecutorConfig {

    private final OpsTraceAwareExecutorFactory executorFactory =
            new OpsTraceAwareExecutorFactory();

    @Bean(name = "opsSubAgentExecutor", destroyMethod = "shutdown")
    @Primary
    public ThreadPoolExecutor opsSubAgentExecutor(
            @Value("${orbisops.multi-agent.executor.core-pool-size:3}") Integer corePoolSize,
            @Value("${orbisops.multi-agent.executor.max-pool-size:6}") Integer maxPoolSize,
            @Value("${orbisops.multi-agent.executor.queue-capacity:32}") Integer queueCapacity,
            @Value("${orbisops.multi-agent.executor.keep-alive-seconds:30}") Long keepAliveSeconds,
            @Value("${orbisops.multi-agent.executor.rejection-policy:CallerRunsPolicy}") String rejectionPolicy) {
        return create(corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds,
                rejectionPolicy, "ops-sub-agent-", 3, 6, 32, "CallerRunsPolicy");
    }

    @Bean(name = "opsRunExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor opsRunExecutor(
            @Value("${orbisops.multi-agent.run-executor.core-pool-size:2}") Integer corePoolSize,
            @Value("${orbisops.multi-agent.run-executor.max-pool-size:4}") Integer maxPoolSize,
            @Value("${orbisops.multi-agent.run-executor.queue-capacity:32}") Integer queueCapacity,
            @Value("${orbisops.multi-agent.run-executor.keep-alive-seconds:30}") Long keepAliveSeconds,
            @Value("${orbisops.multi-agent.run-executor.rejection-policy:CallerRunsPolicy}") String rejectionPolicy) {
        return create(corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds,
                rejectionPolicy, "ops-run-", 2, 4, 32, "CallerRunsPolicy");
    }

    @Bean(name = "opsModelCallExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor opsModelCallExecutor(
            @Value("${orbisops.multi-agent.model-call-executor.core-pool-size:4}") Integer corePoolSize,
            @Value("${orbisops.multi-agent.model-call-executor.max-pool-size:8}") Integer maxPoolSize,
            @Value("${orbisops.multi-agent.model-call-executor.queue-capacity:32}") Integer queueCapacity,
            @Value("${orbisops.multi-agent.model-call-executor.keep-alive-seconds:30}") Long keepAliveSeconds,
            @Value("${orbisops.multi-agent.model-call-executor.rejection-policy:AbortPolicy}") String rejectionPolicy) {
        return create(corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds,
                rejectionPolicy, "ops-model-call-", 4, 8, 32, "AbortPolicy");
    }

    @Bean(name = "ragIngestionExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor ragIngestionExecutor(
            @Value("${orbisops.rag.ingestion.executor.core-pool-size:1}") Integer corePoolSize,
            @Value("${orbisops.rag.ingestion.executor.max-pool-size:2}") Integer maxPoolSize,
            @Value("${orbisops.rag.ingestion.executor.queue-capacity:16}") Integer queueCapacity,
            @Value("${orbisops.rag.ingestion.executor.keep-alive-seconds:30}") Long keepAliveSeconds,
            @Value("${orbisops.rag.ingestion.executor.rejection-policy:CallerRunsPolicy}") String rejectionPolicy) {
        return create(corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds,
                rejectionPolicy, "rag-ingestion-", 1, 2, 16, "CallerRunsPolicy");
    }

    @Bean(name = "opsMemoryExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor opsMemoryExecutor(
            @Value("${orbisops.chat.memory.executor.core-pool-size:3}") Integer corePoolSize,
            @Value("${orbisops.chat.memory.executor.max-pool-size:5}") Integer maxPoolSize,
            @Value("${orbisops.chat.memory.executor.queue-capacity:64}") Integer queueCapacity,
            @Value("${orbisops.chat.memory.executor.keep-alive-seconds:30}") Long keepAliveSeconds,
            @Value("${orbisops.chat.memory.executor.rejection-policy:AbortPolicy}") String rejectionPolicy) {
        String backgroundOnlyRejectionPolicy = "CallerRunsPolicy".equalsIgnoreCase(rejectionPolicy)
                ? "AbortPolicy"
                : rejectionPolicy;
        return create(corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds,
                backgroundOnlyRejectionPolicy, "ops-memory-", 3, 5, 64, "AbortPolicy");
    }

    private ThreadPoolExecutor create(
            Integer corePoolSize,
            Integer maxPoolSize,
            Integer queueCapacity,
            Long keepAliveSeconds,
            String rejectionPolicy,
            String threadNamePrefix,
            int defaultCore,
            int defaultMax,
            int defaultQueueCapacity,
            String defaultRejectionPolicy) {
        return executorFactory.create(OpsExecutorPoolSettings.resolve(
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                keepAliveSeconds,
                rejectionPolicy,
                threadNamePrefix,
                defaultCore,
                defaultMax,
                defaultQueueCapacity,
                defaultRejectionPolicy));
    }
}
