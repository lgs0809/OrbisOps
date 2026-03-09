package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpsProjectWorkspaceRuntimeDirectoryFailureAdapter
        implements ProjectWorkspaceRuntimeDirectoryFailurePort {

    @Override
    public void loadFailed(RuntimeException error) {
        log.warn("业务系统空间持久化加载失败，初始化为空工作区：{}",
                error == null ? "" : error.getMessage());
    }
}
