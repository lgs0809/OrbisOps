package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRuntimeKnowledgeResolverConfiguration {

    @Bean
    public OpsRuntimeKnowledgeResolver opsRuntimeKnowledgeResolver(
            ObjectProvider<ProjectKnowledgeAuthorizationApplicationService> authorizationProvider) {
        return new OpsRuntimeKnowledgeResolver(authorizationProvider::getIfAvailable);
    }
}
