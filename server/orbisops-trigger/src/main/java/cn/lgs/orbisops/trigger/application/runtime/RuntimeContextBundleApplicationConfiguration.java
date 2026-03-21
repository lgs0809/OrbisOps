package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleAuditPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleCreateApplicationService;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleQueryApplicationService;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextCanarySkillPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextSkillSelectionPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextToolsetPort;
import cn.lgs.orbisops.domain.runtime.contextbundle.adapter.repository.IRuntimeContextBundleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeContextBundleApplicationConfiguration {

    @Bean
    public RuntimeContextBundleCreateApplicationService runtimeContextBundleCreateApplicationService(
            IRuntimeContextBundleRepository repository,
            RuntimeContextSkillSelectionPort skillSelection,
            RuntimeContextCanarySkillPort canarySkills,
            RuntimeContextToolsetPort toolsets,
            RuntimeContextBundleAuditPort audit) {
        return new RuntimeContextBundleCreateApplicationService(
                repository, skillSelection, canarySkills, toolsets, audit);
    }

    @Bean
    public RuntimeContextBundleQueryApplicationService runtimeContextBundleQueryApplicationService(
            IRuntimeContextBundleRepository repository) {
        return new RuntimeContextBundleQueryApplicationService(repository);
    }
}
