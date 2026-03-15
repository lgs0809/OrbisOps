package cn.lgs.orbisops.application.project;

import java.util.List;
import java.util.Map;

/** Read-side access to the remaining workspace compatibility projection. */
public interface ProjectWorkspaceQueryPort {

    Map<String, Object> snapshot();

    List<Map<String, Object>> templates();

    Map<String, Object> detail(String projectId);
}
