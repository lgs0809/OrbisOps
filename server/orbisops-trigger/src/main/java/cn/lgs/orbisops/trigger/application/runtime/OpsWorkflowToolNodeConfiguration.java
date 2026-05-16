package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolExecutionPort;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowToolNodeApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsWorkflowToolNodeConfiguration {

    @Bean
    public WorkflowToolNodeApplicationService workflowToolNodeApplicationService(
            WorkflowBoundToolExecutionPort executionPort) {
        return new WorkflowToolNodeApplicationService(executionPort);
    }
}
