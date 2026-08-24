package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.SkillEffectThresholds;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ORBISOPS_DISCOVERY_TEST_JDBC_URL", matches=".+")
class SkillEffectOutcomeMySqlIT {
    JdbcTemplate jdbc;
    JdbcSkillEffectMetricAdapter metrics;
    JdbcSkillRuntimeUsageAdapter usage;
    SkillRuntimeUsageApplicationService service;
    SkillTransactionPort transactions;
    String project, skill, run;
    @BeforeEach void initializeRealLedger() throws Exception {
        var ds = OrbisOpsExternalMySqlFixture.dataSource();
        OrbisOpsExternalMySqlFixture.createTables(ds, "ai_ops_skill_runtime_usage", "ai_ops_skill_effect_metric");
        jdbc = new JdbcTemplate(ds); metrics = new JdbcSkillEffectMetricAdapter(jdbc); usage = new JdbcSkillRuntimeUsageAdapter(jdbc);
        var template = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transactions = new SkillTransactionPort() {public <T> T required(Supplier<T> action) {return template.execute(status -> action.get());}};
        service = service(metrics);
        project = "it-" + UUID.randomUUID(); skill = "skill-" + UUID.randomUUID(); run = "run-" + UUID.randomUUID();
        record(run); record(run + "-neighbor");
    }
    private SkillRuntimeUsageApplicationService service(SkillEffectMetricPort metricPort) {
        return new SkillRuntimeUsageApplicationService(usage, new SkillEffectMetricApplicationService(metricPort, null,
                new SkillEffectThresholds(1, .1, .1, .1, .1, .1, .1)), transactions);
    }
    private void record(String id) {
        service.record(new SkillRuntimeUsageCommand(project, "agent", id, "context", List.of(new SkillRuntimeUsageReference(skill,1,"hash","node")),
                Map.of("success",true,"evidenceSufficient",true,"toolCallCount",3)));
    }
    @Test void correctionAndReverseCorrectionUpdateBothCountersWithoutChangingOtherRunsOrDenominators() {
        assertEquals(2, metrics.metrics(project,skill,1).successfulRunCount());
        assertEquals(1, service.reconcileRunOutcome(project,run,Map.of("success",false,"evidenceSufficient",false)));
        var corrected = metrics.metrics(project,skill,1);
        assertEquals(1,corrected.successfulRunCount()); assertEquals(1,corrected.evidenceSufficientCount());
        assertEquals(2,corrected.usedRunCount()); assertEquals(6,corrected.toolCallCount());
        assertEquals(0, service.reconcileRunOutcome(project,run,Map.of("success",false,"evidenceSufficient",false)));
        assertEquals(corrected,metrics.metrics(project,skill,1));
        assertEquals(1, service.reconcileRunOutcome(project,run,Map.of("success",true,"evidenceSufficient",true)));
        assertEquals(2,metrics.metrics(project,skill,1).successfulRunCount());
        assertEquals(2,metrics.metrics(project,skill,1).evidenceSufficientCount());
        assertEquals(Boolean.TRUE, ((Map<?,?>)usage.listForRun(project,run+"-neighbor").get(0).get("outcome")).get("success"));
    }
    @Test void eightConcurrentIdenticalFeedbacksApplyOneDelta() throws Exception {
        var pool = Executors.newFixedThreadPool(8); var gate = new CountDownLatch(1);
        try {
            var pending = new ArrayList<Future<Integer>>();
            for(int i=0;i<8;i++)pending.add(pool.submit(()->{gate.await();return service.reconcileRunOutcome(project,run,
                    Map.of("success",false,"evidenceSufficient",false,"userNegativeFeedback",true));}));
            gate.countDown();int updates=0;for(var future:pending)updates+=future.get(10,TimeUnit.SECONDS);
            assertEquals(1,updates);var effect=metrics.metrics(project,skill,1);
            assertEquals(1,effect.successfulRunCount());assertEquals(1,effect.evidenceSufficientCount());
            assertEquals(1,effect.userNegativeFeedbackCount());assertEquals(2,effect.usedRunCount());
        } finally {pool.shutdownNow();}
    }
    @Test void projectionFailureRollsBackTheUsageCorrectionInTheSameTransaction() {
        var failing = org.mockito.Mockito.spy(metrics);
        org.mockito.Mockito.doThrow(new IllegalStateException("injected projection failure")).when(failing)
                .applyOutcomeDelta(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyMap());
        assertThrows(IllegalStateException.class,()->service(failing).reconcileRunOutcome(project,run,Map.of("success",false,"evidenceSufficient",false)));
        assertEquals(Boolean.TRUE, ((Map<?,?>)usage.listForRun(project,run).get(0).get("outcome")).get("success"));
        assertEquals(2,metrics.metrics(project,skill,1).successfulRunCount());
        assertEquals(2,metrics.metrics(project,skill,1).evidenceSufficientCount());
    }
}
