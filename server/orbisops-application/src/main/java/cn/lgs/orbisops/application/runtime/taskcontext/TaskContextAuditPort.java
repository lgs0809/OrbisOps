package cn.lgs.orbisops.application.runtime.taskcontext;

import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;

public interface TaskContextAuditPort {

    void record(TaskContextSnapshot before, TaskContextSnapshot after);
}
