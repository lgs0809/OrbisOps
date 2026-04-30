package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEffectMetricApplicationService;
import cn.lgs.orbisops.application.skill.SkillEffectMetricPort;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageApplicationService;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsagePort;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageRecord;
import cn.lgs.orbisops.application.skill.SkillTransactionPort;
import cn.lgs.orbisops.domain.skill.model.SkillEffectThresholds;
import cn.lgs.orbisops.domain.skill.service.SkillEffectDegradationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillRuntimeUsageRecorderTest {

    @Test
    void reconcilesApprovalAndLandingFactsAgainstTheOriginalRunUsage() {
        SkillRuntimeUsagePort usagePort = mock(SkillRuntimeUsagePort.class);
        SkillEffectMetricPort metricPort = mock(SkillEffectMetricPort.class);
        when(usagePort.lockForRun("p1", "run-1")).thenReturn(List.of(
                new SkillRuntimeUsageRecord(
                        7L,
                        "diagnosis",
                        3,
                        Map.of(
                                "success",
                                true,
                                "changePackageApproved",
                                false),
                        "original-json")));
        when(usagePort.compareAndSetOutcome(
                eq(7L),
                eq("original-json"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(true);
        SkillEffectMetricApplicationService metrics =
                new SkillEffectMetricApplicationService(
                        metricPort,
                        new SkillEffectDegradationPolicy(),
                        new SkillEffectThresholds(
                                10,
                                0.25D,
                                0.30D,
                                0.20D,
                                0.15D,
                                0.15D,
                                0.50D));
        OpsSkillRuntimeUsageRecorder recorder =
                new OpsSkillRuntimeUsageRecorder(
                        new SkillRuntimeUsageApplicationService(
                                usagePort,
                                metrics,
                                directTransactionPort()));

        int updated = recorder.reconcileRunOutcome(
                "p1",
                "run-1",
                Map.of(
                        "changePackageApproved",
                        true,
                        "landingSucceeded",
                        true));

        assertEquals(1, updated);
        verify(metricPort).applyOutcomeDelta(
                eq("p1"),
                eq("diagnosis"),
                eq(3),
                eq(Map.of(
                        "success",
                        true,
                        "changePackageApproved",
                        false)),
                eq(Map.of(
                        "success",
                        true,
                        "changePackageApproved",
                        true,
                        "landingSucceeded",
                        true)));
    }

    private SkillTransactionPort directTransactionPort() {
        return new SkillTransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
