package cn.lgs.orbisops.application.project;

import java.util.Map;

public interface ProjectResourcePreparationPort {

    ProjectResourcePreparation prepare(ProjectResourcePreparationRequest request);

    Map<String, Object> enrichPermission(String type, Map<String, Object> permission);
}
