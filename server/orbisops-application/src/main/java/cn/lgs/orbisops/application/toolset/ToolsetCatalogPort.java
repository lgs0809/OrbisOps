package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolsetDefinition;

import java.util.List;
import java.util.Map;

/** Typed catalog boundary for Toolset aggregate lifecycle operations. */
public interface ToolsetCatalogPort {

    List<ToolsetDefinition> listBuiltIn();

    List<ToolsetDefinition> listCustom(String projectId);

    List<ToolsetDefinition> listEffective(String projectId, String userId);

    ToolsetDefinition registerCustom(
            String projectId,
            Map<String, Object> normalized,
            String actor);

    ToolsetDefinition setEnabled(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor);

    ToolsetRefreshOutcome refreshMcp(
            String projectId,
            String toolsetId,
            String actor);
}
