package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Retries alert-trigger outbox records when webhook submission failed transiently. */
@Slf4j
@Component
public class OpsAlertTriggerOutboxJob {

    private final OpsAlertTriggerService alertTriggerService;
    private final OpsAnalysisApplicationService analysisApplicationService;
    private final OpsAlertOutboxJobSettings settings;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OpsAlertTriggerOutboxJob(
            OpsAlertTriggerService alertTriggerService,
            OpsAnalysisApplicationService analysisApplicationService) {
        this(
                alertTriggerService,
                analysisApplicationService,
                OpsAlertOutboxJobSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsAlertTriggerOutboxJob(
            OpsAlertTriggerService alertTriggerService,
            OpsAnalysisApplicationService analysisApplicationService,
            OpsAlertOutboxJobSettings settings) {
        this.alertTriggerService = alertTriggerService;
        this.analysisApplicationService = analysisApplicationService;
        this.settings = settings == null ? OpsAlertOutboxJobSettings.defaults() : settings;
    }

    @Scheduled(fixedDelayString = "${orbisops.alert-triggers.outbox.fixed-delay-ms:30000}")
    public void process() {
        if (!settings.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer =
                    analysisApplicationService::buildAnalysis;
            AlertTriggerOutboxOutcome result = alertTriggerService.processPendingOutbox(
                    settings.batchSize(),
                    analyzer);
            if (result.hasActivity()) {
                log.info("告警 outbox 自动处理完成 result={}", result);
            }
        } catch (Exception error) {
            log.warn("告警 outbox 自动处理失败：{}", error.getMessage());
        } finally {
            running.set(false);
        }
    }
}
