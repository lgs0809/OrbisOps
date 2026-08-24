package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillReleaseService;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRuntimeSkillResolverConfiguration {

    @Bean
    public OpsRuntimeFrozenSkillContextResolver opsRuntimeFrozenSkillContextResolver(
            ObjectProvider<SkillCatalogQueryService> catalogQueryProvider,
            ObjectProvider<OpsSkillReleaseService> releaseServiceProvider,
            OpsRuntimeSkillSettings settings, SkillRuntimeBudgetPort budget) {
        return new OpsRuntimeFrozenSkillContextResolver(
                catalogQueryProvider::getIfAvailable,
                releaseServiceProvider::getIfAvailable,
                settings, budget);
    }

    @Bean
    public OpsRuntimeSkillResolver opsRuntimeSkillResolver(
            ObjectProvider<SkillRuntimeToolProvider> skillToolProvider,
            ObjectProvider<ProjectSkillAuthorizationApplicationService> authorizationProvider,
            ObjectProvider<OpsProjectSkillToolProvider> projectSkillToolProvider,
            OpsRuntimeFrozenSkillContextResolver frozenContextResolver,
            OpsRuntimeSkillSettings settings) {
        return new OpsRuntimeSkillResolver(
                skillToolProvider::getIfAvailable,
                authorizationProvider::getIfAvailable,
                projectSkillToolProvider::getIfAvailable,
                frozenContextResolver,
                settings);
    }
}
