package cn.lgs.orbisops.application.episode;

import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import java.util.Map;

public interface TaskAcceptancePort {
    Map<String,Object> inspect(String project, String episode, String actor, boolean admin);
    default Map<String,Object> draftEvidence(String project, String episode, String actor, boolean admin) {
        throw new IllegalStateException("TASK_ACCEPTANCE_DRAFT_UNAVAILABLE");
    }
    Map<String,Object> verify(String project, String episode, TaskAcceptanceRequest request, String actor, boolean admin);
}
