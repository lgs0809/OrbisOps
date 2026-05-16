package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.workflow.WorkflowToolCallBudgetApplicationService;
import cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMcpCallBudgetConfiguration {
    @Bean
    public WorkflowToolCallBudgetApplicationService workflowToolCallBudgetApplicationService(IWorkflowToolCallBudgetRepository repository) {
        return new WorkflowToolCallBudgetApplicationService(repository);
    }
}
