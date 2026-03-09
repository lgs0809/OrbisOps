package cn.lgs.orbisops.application.project;

import java.util.List;

/** Project directory facts required by access authorization. */
public interface ProjectAccessPort {

    boolean exists(String projectId);

    boolean owner(String projectId, String username, String userId);

    List<ProjectCatalogEntry> publicCatalog();
}
