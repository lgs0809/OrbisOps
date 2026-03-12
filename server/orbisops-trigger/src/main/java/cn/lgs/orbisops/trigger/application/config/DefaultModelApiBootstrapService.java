package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapPlan;
import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapResult;
import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapUseCase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Startup facade for the optional default model API bootstrap process. */
@Slf4j
@Service
public class DefaultModelApiBootstrapService {

    private final DefaultModelApiBootstrapUseCase bootstrapUseCase;
    private final DefaultModelApiBootstrapPlan plan;

    public DefaultModelApiBootstrapService(
            DefaultModelApiBootstrapUseCase bootstrapUseCase,
            DefaultModelApiBootstrapPlan plan) {
        if (bootstrapUseCase == null) {
            throw new IllegalArgumentException("DEFAULT_MODEL_API_BOOTSTRAP_USE_CASE_REQUIRED");
        }
        if (plan == null) {
            throw new IllegalArgumentException("DEFAULT_MODEL_API_BOOTSTRAP_PLAN_REQUIRED");
        }
        this.bootstrapUseCase = bootstrapUseCase;
        this.plan = plan;
    }

    @PostConstruct
    public void bootstrap() {
        DefaultModelApiBootstrapResult result = bootstrapUseCase.bootstrap(plan);
        if (result.action() == DefaultModelApiBootstrapResult.Action.CREATED) {
            log.info(
                    "已创建默认模型 API 配置，apiId={}，baseUrl={}",
                    result.apiId(),
                    result.baseUrl());
        } else if (result.action() == DefaultModelApiBootstrapResult.Action.MIGRATED) {
            log.info(
                    "已将占位模型 API 配置迁移为环境 Secret 引用，apiId={}，baseUrl={}",
                    result.apiId(),
                    result.baseUrl());
        }
    }
}
