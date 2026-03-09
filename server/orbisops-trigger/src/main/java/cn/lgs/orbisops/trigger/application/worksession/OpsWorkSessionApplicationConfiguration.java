package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;
import cn.lgs.orbisops.application.worksession.WorkSessionLifecyclePort;
import cn.lgs.orbisops.application.worksession.WorkSessionProcessManager;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryExecutionPort;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRuntimeContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsWorkSessionApplicationConfiguration {

    @Bean
    public ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSessionUseCase(
            WorkSessionLifecyclePort<OpsAgentChatRequest, OpsWorkSessionRuntimeContext,
                    OpsAgentChatResponse, OpsRuntimeEvent> port) {
        return new ExecuteWorkSessionUseCase<>(port);
    }

    @Bean
    public WorkSessionProcessManager workSessionProcessManager(
            WorkSessionRecoveryPort recoveryPort,
            WorkSessionRecoveryExecutionPort recoveryExecution) {
        return new WorkSessionProcessManager(recoveryPort, recoveryExecution);
    }

    @Bean
    public ToolLoopCoordinator toolLoopCoordinator() {
        return new ToolLoopCoordinator();
    }
}
