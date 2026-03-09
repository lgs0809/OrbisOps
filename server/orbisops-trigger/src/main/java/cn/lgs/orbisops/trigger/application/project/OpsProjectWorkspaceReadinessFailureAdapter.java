package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectWorkspaceReadinessFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpsProjectWorkspaceReadinessFailureAdapter implements ProjectWorkspaceReadinessFailurePort {

    @Override
    public void readinessQueryFailed(String catalog, String projectId, RuntimeException error) {
        log.warn("检查项目{}就绪状态失败，按未接入处理，projectId={} reason={}",
                catalog,
                projectId,
                error == null ? "" : error.getMessage());
    }
}
