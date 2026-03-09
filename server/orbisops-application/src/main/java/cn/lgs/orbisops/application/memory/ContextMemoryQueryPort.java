package cn.lgs.orbisops.application.memory;

import java.util.List;

@FunctionalInterface
public interface ContextMemoryQueryPort {

    List<ContextMemoryView> listForScene(String scene, String userId, String projectId, int limit);
}
